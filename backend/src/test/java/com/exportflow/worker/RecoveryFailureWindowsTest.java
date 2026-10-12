// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

// [AC-010](../../../../../../../docs/product/acceptance-criteria.md#ac-010)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
class RecoveryFailureWindowsTest extends BoundaryFixture {
  @ParameterizedTest
  @CsvSource({
    "com.exportflow.exporttasks.port.ExportFileStorage,writeBlock,after",
    "com.exportflow.worker.port.CheckpointRepository,commitBlock,before",
    "com.exportflow.worker.port.CheckpointRepository,commitBlock,after",
    "com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler,assemble,before",
    "com.exportflow.exporttasks.port.ExportFileStorage,readBlock,after",
    "com.exportflow.exporttasks.port.ExportFileStorage,publish,after",
    "com.exportflow.exporttasks.port.ArtifactRepository,registerReady,after",
    "com.exportflow.worker.port.TaskExecutionRepository,complete,after"
  })
  void TC235_realKillAtEveryDurabilityWindowRecoversSameCommand(
      String port, String method, String phase) throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var originalCommand = outbox(task).get("event_id");
    call(DISPATCH, "scanAvailable");
    var worker =
        startWorker(
            List.of(
                "--test-barrier-port=" + port,
                "--test-barrier-method=" + method,
                "--test-barrier-phase=" + phase,
                "--test-barrier-marker=window.marker",
                "--test-barrier-occurrence=" + (method.equals("readBlock") ? 2 : 1)));
    try {
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(90))
          .untilAsserted(
              () -> {
                assertThat(worker.isAlive()).isTrue();
                assertThat(root.resolve("window.marker")).exists();
              });
      var readyBefore =
          db.queryForList(
              "SELECT artifact_id,checksum FROM export_artifacts WHERE task_id=? AND state IN ('READY','ADOPTED')",
              task.toString());
      worker.destroyForcibly();
      assertThat(worker.waitFor(20, TimeUnit.SECONDS)).isTrue();
      expireExecution(task);
      var resumed = startWorker(false);
      try {
        awaitSuccess(task);
      } finally {
        stopWorker(resumed);
      }
      assertThat(count("export_tasks")).isEqualTo(1);
      assertThat(count("command_outbox")).isEqualTo(1);
      assertThat(outbox(task).get("event_id")).isEqualTo(originalCommand);
      var adopted =
          db.queryForList(
              "SELECT artifact_id,checksum FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
              task.toString());
      assertThat(adopted).hasSize(1);
      if (!readyBefore.isEmpty()) assertThat(adopted.getFirst()).isEqualTo(readyBefore.getFirst());
      assertThat(
              db.queryForObject(
                  "SELECT processed_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(2501);
      assertThat(download(task)).isNotEmpty();
    } finally {
      if (worker.isAlive()) stopWorker(worker);
    }
  }

  @Test
  void TC235_blockCommitResponseLostIsReconciledWithoutDuplicatingBlocks() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    var lost = new AtomicBoolean();
    probe.on(
        CHECKPOINT,
        "commitBlock",
        (m, a, real) -> {
          Object result = real.run();
          if (property(result, "kind").toString().equals("APPLIED")
              && lost.compareAndSet(false, true))
            throw new java.sql.SQLRecoverableException(
                "synthetic block commit acknowledgement lost");
          return result;
        });
    var result = generate(task, lease);
    assertThat(lost).isTrue();
    if (property(result, "kind").toString().equals("UNKNOWN")) {
      probe.clear();
      expireExecution(task);
      lease = property(claim(task, "synthetic-recovery"), "lease");
      result = generate(task, lease);
    }
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(call(EXEC_SERVICE, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForObject(
                "SELECT SUM(row_count) FROM export_data_blocks b JOIN export_checkpoints c ON b.task_id=c.task_id AND b.data_generation=c.data_generation WHERE b.task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(2501);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "checksum",
        "byte_size",
        "sequence",
        "format_version",
        "configuration_hash",
        "last_cursor_json",
        "tail",
        "generation"
      })
  void TC235_corruptCheckpointNeverSilentlyAdoptsInvalidPrefix(String defect) throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var originalLease = lease(task);
    probe.on(
        STORAGE,
        "readBlock",
        (m, a, real) -> {
          throw new java.nio.file.AccessDeniedException("synthetic temporary storage unavailable");
        });
    assertThat(property(generate(task, originalLease), "kind").toString()).isEqualTo("UNKNOWN");
    probe.clear();
    var blocks =
        db.queryForList(
            "SELECT * FROM export_data_blocks WHERE task_id=? ORDER BY sequence", task.toString());
    assertThat(blocks).isNotEmpty();
    long generation =
        db.queryForObject(
            "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
            Long.class,
            task.toString());
    var first = blocks.getFirst();
    String block = first.get("block_id").toString();
    switch (defect) {
      case "checksum" ->
          db.update(
              "UPDATE export_data_blocks SET checksum=? WHERE block_id=?", "0".repeat(64), block);
      case "byte_size" ->
          db.update("UPDATE export_data_blocks SET byte_size=byte_size+1 WHERE block_id=?", block);
      case "sequence" ->
          db.update("UPDATE export_data_blocks SET sequence=999999 WHERE block_id=?", block);
      case "format_version" ->
          db.update("UPDATE export_data_blocks SET format_version=999 WHERE block_id=?", block);
      case "configuration_hash" ->
          db.update(
              "UPDATE export_data_blocks SET configuration_hash=? WHERE block_id=?",
              "f".repeat(64),
              block);
      case "last_cursor_json" ->
          db.update("UPDATE export_data_blocks SET last_cursor_json='{}' WHERE block_id=?", block);
      case "generation" ->
          db.update(
              "UPDATE export_data_blocks SET data_generation=data_generation+99 WHERE block_id=?",
              block);
      case "tail" -> {
        Path file =
            root.resolve("tasks")
                .resolve(task.toString())
                .resolve("data")
                .resolve(Long.toString(generation))
                .resolve("blocks")
                .resolve(block + ".jsonl");
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
          channel.truncate(20);
        }
      }
    }
    expireExecution(task);
    var next = property(claim(task, "synthetic-new-generation"), "lease");
    var result = generate(task, next);
    // 可验证安全重建或明确不可恢复，绝不能把被破坏的代次当可信完整结果。
    if (property(result, "kind").toString().equals("READY")) {
      long newGeneration =
          db.queryForObject(
              "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
              Long.class,
              task.toString());
      assertThat(newGeneration).isGreaterThan(generation);
      assertThat(call(EXEC_SERVICE, "complete", next, result).toString()).isEqualTo("APPLIED");
      assertThat(
              db.queryForObject(
                  "SELECT processed_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(2501);
    } else {
      assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
      assertThat(count("export_artifacts")).isZero();
    }
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isGreaterThanOrEqualTo(blocks.size());
  }

  @Test
  void TC235_temporaryReadFailureKeepsGenerationReferencesAndHighWater() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var old = lease(task);
    probe.on(
        STORAGE,
        "readBlock",
        (m, a, real) -> {
          throw new java.nio.file.AccessDeniedException("synthetic unavailable");
        });
    assertThat(property(generate(task, old), "kind").toString()).isEqualTo("UNKNOWN");
    var checkpoint =
        db.queryForMap("SELECT * FROM export_checkpoints WHERE task_id=?", task.toString());
    var blocks =
        db.queryForList(
            "SELECT * FROM export_data_blocks WHERE task_id=? ORDER BY sequence", task.toString());
    expireExecution(task);
    var next = property(claim(task, "synthetic-next"), "lease");
    assertThat(property(generate(task, next), "kind").toString()).isEqualTo("UNKNOWN");
    assertThat(db.queryForMap("SELECT * FROM export_checkpoints WHERE task_id=?", task.toString()))
        .isEqualTo(checkpoint);
    assertThat(
            db.queryForList(
                "SELECT * FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
                task.toString()))
        .isEqualTo(blocks);
    assertThat(state(task)).isEqualTo("RUNNING");
    probe.clear();
    assertThat(property(generate(task, next), "kind").toString()).isEqualTo("READY");
  }

  @ParameterizedTest
  @ValueSource(strings = {"checksum", "byte_size", "row_count", "configuration_hash"})
  void TC235_readyProofMismatchCannotBeAdopted(String field) throws Exception {
    var task = create();
    var old = lease(task);
    var ready = generate(task, old);
    assertThat(property(ready, "kind").toString()).isEqualTo("READY");
    if (field.equals("checksum") || field.equals("configuration_hash"))
      db.update(
          "UPDATE export_artifacts SET " + field + "=? WHERE task_id=?",
          "0".repeat(64),
          task.toString());
    else
      db.update(
          "UPDATE export_artifacts SET " + field + "=" + field + "+1 WHERE task_id=?",
          task.toString());
    expireExecution(task);
    var next = property(claim(task, "synthetic-adopter"), "lease");
    var checked = generate(task, next);
    assertThat(property(checked, "kind").toString()).isNotEqualTo("READY");
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                Long.class,
                task.toString()))
        .isZero();
  }

  @Test
  void TC235_concurrentTakeoverChargesOnlyWinnerAndRestartKeepsBudgetAndBackoff() throws Exception {
    var task = create();
    lease(task);
    expireExecution(task);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var barrier = new CyclicBarrier(2);
      var a =
          pool.submit(
              () -> {
                barrier.await();
                return claim(task, "synthetic-a");
              });
      var b =
          pool.submit(
              () -> {
                barrier.await();
                return claim(task, "synthetic-b");
              });
      assertThat(
              List.of(
                  property(a.get(20, TimeUnit.SECONDS), "kind").toString(),
                  property(b.get(20, TimeUnit.SECONDS), "kind").toString()))
          .containsExactlyInAnyOrder("ACQUIRED", "BUSY");
    }
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isEqualTo(1);
    var before =
        db.queryForMap(
            "SELECT recovery_count,next_recovery_at,attempt_deadline,fence FROM task_execution WHERE task_id=?",
            task.toString());
    restartApplication();
    assertThat(
            db.queryForMap(
                "SELECT recovery_count,next_recovery_at,attempt_deadline,fence FROM task_execution WHERE task_id=?",
                task.toString()))
        .isEqualTo(before);
    db.update(
        "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()+INTERVAL 30 SECOND WHERE task_id=?",
        task.toString());
    assertThat(property(claim(task, "synthetic-too-early"), "kind").toString()).isEqualTo("BUSY");
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isEqualTo(1);
  }
}
