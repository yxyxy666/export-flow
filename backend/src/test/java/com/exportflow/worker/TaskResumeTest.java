// [规格与证据](../../../../../../../specs/005-task-resume-and-live-progress/tasks.md)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.testing.TaskManagementFixture;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// [AC-092](../../../../../../../docs/product/acceptance-criteria.md#ac-092)
// [AC-093](../../../../../../../docs/product/acceptance-criteria.md#ac-093)
// [AC-090](../../../../../../../docs/product/acceptance-criteria.md#ac-090)
class TaskResumeTest extends TaskManagementFixture {
  private HttpResponse<String> resume(UUID task, UUID operation, String version) throws Exception {
    return resumeBody(
        task,
        json.writeValueAsString(
            Map.of("operationId", operation.toString(), "expectedControlVersion", version)));
  }

  private HttpResponse<String> resumeBody(UUID task, String payload) throws Exception {
    return http.send(
        HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/" + task + "/resume"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private void pauseRunning(UUID task, Object lease) throws Exception {
    assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
    var proof =
        record(
            method(EXECUTION, "acknowledgePause").getParameterTypes()[0],
            Map.of(
                "taskId",
                task,
                "owner",
                property(lease, "owner"),
                "fence",
                property(lease, "fence"),
                "requestedControlVersion",
                1L,
                "lastCheckpointVersion",
                ((Number) property(port(CHECKPOINT, "find", task), "version")).longValue()));
    assertThat(property(port(EXECUTION, "acknowledgePause", proof), "kind")).isEqualTo("APPLIED");
    assertThat(state(task)).isEqualTo("PAUSED");
  }

  @Test
  void TC501_pendingResumeReopensOriginalCommandAndReplaysCurrentSummary() throws Exception {
    var task = create();
    var event = outbox(task).get("event_id");
    assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
    assertThat(listedTask(task).path("allowedActions").toString()).contains("RESUME");
    var operation = UUID.randomUUID();
    var first = resume(task, operation, "1");
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(body(first).path("task").path("status").asText()).isEqualTo("PENDING");
    assertThat(body(first).path("task").path("controlVersion").asText()).isEqualTo("2");
    assertThat(outbox(task)).containsEntry("event_id", event).containsEntry("status", "READY");
    var lease = lease(task);
    assertThat(lease).isNotNull();
    var replay = resume(task, operation, "1");
    assertThat(body(replay).path("outcome").asText()).isEqualTo("REPLAY");
    assertThat(body(replay).path("task").path("status").asText()).isEqualTo("RUNNING");
    var completed = generate(task, lease);
    assertThat(property(completed, "kind").toString()).isEqualTo("READY");
    assertThat(port(EXECUTION, "complete", lease, completed).toString()).isEqualTo("APPLIED");
    assertThat(body(resume(task, operation, "1")).path("task").path("status").asText())
        .isEqualTo("SUCCEEDED");
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM task_resume_operations", Long.class))
        .isEqualTo(1);
  }

  @Test
  void TC501_invalidStateVersionAndMalformedRequestsHaveNoPartialEffects() throws Exception {
    var task = create();
    assertThat(resume(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(409);
    assertThat(resume(UUID.randomUUID(), UUID.randomUUID(), "0").statusCode()).isEqualTo(404);
    pause(task, UUID.randomUUID(), "0");
    for (String payload :
        List.of(
            "{}",
            "{\"operationId\":\"bad\",\"expectedControlVersion\":\"1\"}",
            "{\"operationId\":\""
                + UUID.randomUUID()
                + "\",\"expectedControlVersion\":\"1\",\"extra\":true}",
            "{\"operationId\":\""
                + UUID.randomUUID()
                + "\",\"expectedControlVersion\":\"9223372036854775808\"}",
            "{\"operationId\":\""
                + UUID.randomUUID()
                + "\",\"expectedControlVersion\":\"1\",\"expectedControlVersion\":\"1\"}",
            " ".repeat(4097))) assertThat(resumeBody(task, payload).statusCode()).isEqualTo(400);
    assertThat(resume(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(409);
    assertThat(state(task)).isEqualTo("PAUSED");
    assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM task_resume_operations", Long.class))
        .isZero();
  }

  @Test
  void TC502_crossActionIdsAreRejectedAndUnknownResponseFindsCommittedResume() throws Exception {
    var task = create();
    var pauseId = UUID.randomUUID();
    pause(task, pauseId, "0");
    assertThat(resume(task, pauseId, "1").statusCode()).isEqualTo(409);
    var id = UUID.randomUUID();
    probe.on(
        "com.exportflow.exporttasks.port.TaskManagementRepository",
        "resume",
        (m, a, real) -> {
          real.run();
          throw new IllegalStateException("synthetic lost response");
        });
    try {
      var unknown = resume(task, id, "1");
      assertThat(unknown.statusCode()).isEqualTo(503);
      assertThat(body(unknown).path("outcome").asText()).isEqualTo("UNKNOWN");
    } finally {
      probe.clear();
    }
    assertThat(body(resume(task, id, "1")).path("outcome").asText()).isEqualTo("REPLAY");
    assertThat(pause(task, id, "2").statusCode()).isEqualTo(409);
    assertThat(resume(task, id, "2").statusCode()).isEqualTo(409);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM task_resume_operations", Long.class))
        .isEqualTo(1);
  }

  @Test
  void TC502_concurrentResumesProduceOneControlTransition() throws Exception {
    var task = create();
    pause(task, UUID.randomUUID(), "0");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var barrier = new CyclicBarrier(2);
      var a =
          pool.submit(
              () -> {
                barrier.await();
                return resume(task, UUID.randomUUID(), "1").statusCode();
              });
      var b =
          pool.submit(
              () -> {
                barrier.await();
                return resume(task, UUID.randomUUID(), "1").statusCode();
              });
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(listedTask(task).path("controlVersion").asText()).isEqualTo("2");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM task_resume_operations", Long.class))
        .isEqualTo(1);
  }

  @Test
  void TC502_newLeaseRejectsOldOwnerAndPreservesRecoveryHistory() throws Exception {
    var task = create();
    var dispatch = reserve(task);
    var old = lease(task);
    collect(task, old);
    pauseRunning(task, old);
    db.update("UPDATE task_execution SET recovery_count=1 WHERE task_id=?", task.toString());
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    assertThat(port(OUTBOX, "markSent", dispatch, evidence())).isEqualTo(false);
    assertThat(outbox(task).get("attempt_no")).isEqualTo(1);
    var current = lease(task);
    assertThat(((Number) property(current, "fence")).longValue())
        .isGreaterThan(((Number) property(old, "fence")).longValue());
    assertThat(property(current, "controlVersion")).isEqualTo(2L);
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isEqualTo(1);
    assertThat(port(EXECUTION, "renew", old).toString()).isEqualTo("STALE");
    assertThat(port(EXECUTION, "complete", old, failure("SYNTHETIC")).toString())
        .isEqualTo("STALE");
    var result = generate(task, current);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(port(EXECUTION, "complete", current, result).toString()).isEqualTo("APPLIED");
    assertThat(listedTask(task).path("progressPercent").asInt()).isEqualTo(100);
  }

  @Test
  void TC502_progressWriteFailureRollsBackReceiptTaskAndCommandTogether() throws Exception {
    var task = create();
    pause(task, UUID.randomUUID(), "0");
    var before = listedTask(task);
    probe.on(
        "com.exportflow.progress.port.ProgressRepository",
        "recordChange",
        (m, a, real) -> {
          real.run();
          throw new org.springframework.dao.DataAccessResourceFailureException(
              "synthetic notification write failure");
        });
    try {
      var failed = resume(task, UUID.randomUUID(), "1");
      assertThat(failed.statusCode()).isEqualTo(503);
      assertThat(body(failed).path("outcome").asText()).isEqualTo("UNKNOWN");
      assertThat(listedTask(task)).isEqualTo(before);
      assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
      assertThat(db.queryForObject("SELECT COUNT(*) FROM task_resume_operations", Long.class))
          .isZero();
    } finally {
      probe.clear();
    }
  }

  private UUID partialTask() throws Exception {
    useThreeSmallBlocks();
    useOrderVolume(8);
    var task = createVolume();
    var lease = lease(task);
    var batches = new AtomicInteger();
    probe.on(
        ORDERS,
        "readBatch",
        (m, a, real) -> {
          if (batches.incrementAndGet() == 4) throw new ExecutionStoppedException();
          a[3] = 2;
          return real.run();
        });
    assertThatThrownBy(() -> collect(task, lease)).isInstanceOf(Exception.class);
    assertThat(
            db.queryForObject(
                "SELECT verified_prefix_count FROM export_checkpoints WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(6);
    probe.clear();
    pauseRunning(task, lease);
    return task;
  }

  @Test
  void TC503_partialResumeRepairsMissingMiddleAndKeepsHealthyLaterBlockBeforeTail()
      throws Exception {
    var task = partialTask();
    var blocks = ((CheckpointSnapshot) port(CHECKPOINT, "find", task)).blocks();
    Files.delete((Path) port(STORAGE, "resolve", blocks.get(1).storageRef()));
    var observed = new AtomicInteger();
    probe.on(
        ORDERS,
        "readSegment",
        (m, a, real) -> {
          assertThat(listedTask(task).path("processedCount").asLong()).isEqualTo(4);
          assertThat(listedTask(task).path("repairState").asText()).isEqualTo("REBUILDING");
          observed.incrementAndGet();
          return real.run();
        });
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    var current = lease(task);
    var result = generate(task, current);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(observed).hasValue(1);
    var active =
        db.queryForList(
            "SELECT active_block_id FROM export_data_segments WHERE task_id=? ORDER BY sequence",
            String.class,
            task.toString());
    assertThat(active).hasSize(4);
    assertThat(active.get(0)).isEqualTo(blocks.get(0).blockId().toString());
    assertThat(active.get(1)).isNotEqualTo(blocks.get(1).blockId().toString());
    assertThat(active.get(2)).isEqualTo(blocks.get(2).blockId().toString());
    assertThat(port(EXECUTION, "complete", current, result).toString()).isEqualTo("APPLIED");
    assertThat(listedTask(task).path("progressPercent").asInt()).isEqualTo(100);
    var download =
        http.send(
            HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/" + task + "/file"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(download.statusCode()).isEqualTo(200);
    try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(download.body()))) {
      var sheet = book.getSheetAt(0);
      var source =
          db.queryForList(
              "SELECT order_no FROM orders ORDER BY created_at DESC,id DESC LIMIT 8", String.class);
      assertThat(sheet.getLastRowNum()).isEqualTo(8);
      for (int i = 0; i < 8; i++)
        assertThat(sheet.getRow(i + 1).getCell(0).getStringCellValue()).isEqualTo(source.get(i));
    }
  }

  @Test
  void TC504_partialResumeRebuildsMissingBlockRecordUsingIndependentBaseline() throws Exception {
    var task = partialTask();
    var before = ((CheckpointSnapshot) port(CHECKPOINT, "find", task)).blocks();
    db.update(
        "DELETE FROM export_data_blocks WHERE block_id=?", before.get(1).blockId().toString());
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    var result = generate(task, lease(task));
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(probe.calls(ORDERS, "readSegment")).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT active_block_id FROM export_data_segments WHERE task_id=? AND sequence=3",
                String.class,
                task.toString()))
        .isEqualTo(before.get(2).blockId().toString());
  }

  @Test
  void TC504_missingBaselineCannotInventTrustFromSurvivingFile() throws Exception {
    var task = partialTask();
    db.update("DELETE FROM export_data_segments WHERE task_id=? AND sequence=2", task.toString());
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    var result = generate(task, lease(task));
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code")).isEqualTo("DATA_REPAIR_UNRECOVERABLE");
    assertThat(probe.calls(ORDERS, "readSegment")).isZero();
    assertThat(count("export_artifacts")).isZero();
  }

  @Test
  void TC504_missingCheckpointWithCommittedProgressFailsRatherThanRestartingFromZero()
      throws Exception {
    var task = partialTask();
    long priorReads = probe.calls(ORDERS, "readBatch");
    db.update("DELETE FROM export_checkpoints WHERE task_id=?", task.toString());
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    var result = generate(task, lease(task));
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code")).isEqualTo("DATA_REPAIR_UNRECOVERABLE");
    assertThat(probe.calls(ORDERS, "readBatch")).isEqualTo(priorReads);
  }

  @Test
  void TC504_unknownDatabaseDuringResumeDoesNotRebuildOrAdopt() throws Exception {
    var task = partialTask();
    assertThat(resume(task, UUID.randomUUID(), "1").statusCode()).isEqualTo(200);
    var current = lease(task);
    probe.on(
        REPAIR,
        "load",
        (m, a, real) -> {
          throw new org.springframework.dao.DataAccessResourceFailureException(
              "synthetic unavailable");
        });
    assertThat(property(generate(task, current), "kind").toString()).isEqualTo("UNKNOWN");
    assertThat(probe.calls(ORDERS, "readSegment")).isZero();
    assertThat(count("export_artifacts")).isZero();
    assertThat(state(task)).isEqualTo("RUNNING");
  }
}
