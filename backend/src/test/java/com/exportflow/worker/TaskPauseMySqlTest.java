// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.TaskManagementFixture;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-083](../../../../../../../docs/product/acceptance-criteria.md#ac-083)
// [AC-084](../../../../../../../docs/product/acceptance-criteria.md#ac-084)
// [AC-086](../../../../../../../docs/product/acceptance-criteria.md#ac-086)
// [AC-087](../../../../../../../docs/product/acceptance-criteria.md#ac-087)
class TaskPauseMySqlTest extends TaskManagementFixture {
  @Test
  void TC407_sameOperationAcrossConcurrentTasksCommitsExactlyOnePause() throws Exception {
    var first = create();
    var second = create();
    var operation = UUID.randomUUID();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var barrier = new CyclicBarrier(2);
      var a =
          threads.submit(
              () -> {
                barrier.await();
                return pause(first, operation, "0");
              });
      var b =
          threads.submit(
              () -> {
                barrier.await();
                return pause(second, operation, "0");
              });
      assertThat(
              List.of(
                  a.get(20, TimeUnit.SECONDS).statusCode(),
                  b.get(20, TimeUnit.SECONDS).statusCode()))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(List.of(state(first), state(second))).containsExactlyInAnyOrder("PAUSED", "PENDING");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM task_pause_operations", Long.class))
        .isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(2);
  }

  @Test
  void TC405_staleOwnerFenceControlOrCheckpointCannotProvePauseStopped() throws Exception {
    var task = create();
    var lease = lease(task);
    collect(task, lease);
    var response = pause(task, UUID.randomUUID(), "0");
    long checkpoint =
        db.queryForObject(
            "SELECT checkpoint_version FROM export_checkpoints WHERE task_id=?",
            Long.class,
            task.toString());
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
                body(response).path("task").path("controlVersion").asLong(),
                "lastCheckpointVersion",
                checkpoint));
    for (var change :
        List.<Map<String, Object>>of(
            Map.of("owner", "other-owner"),
            Map.of("fence", ((Number) property(lease, "fence")).longValue() + 1),
            Map.of("requestedControlVersion", 0L),
            Map.of("lastCheckpointVersion", checkpoint + 1))) {
      assertThat(property(port(EXECUTION, "acknowledgePause", copy(proof, change)), "kind"))
          .isEqualTo("WAITING");
      assertThat(state(task)).isEqualTo("PAUSING");
    }
    assertThat(property(port(EXECUTION, "acknowledgePause", proof), "kind")).isEqualTo("APPLIED");
    assertThat(state(task)).isEqualTo("PAUSED");
  }

  @Test
  void TC407_committedPauseResponseLostIsUnknownThenSameOperationFindsCurrentSummary()
      throws Exception {
    var task = create();
    var operation = UUID.randomUUID();
    probe.on(
        "com.exportflow.exporttasks.port.TaskManagementRepository",
        "pause",
        (method, args, real) -> {
          real.run();
          throw new IllegalStateException("synthetic response lost after commit");
        });
    try {
      var response = pause(task, operation, "0");
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(body(response).path("outcome").asText()).isEqualTo("UNKNOWN");
    } finally {
      probe.clear();
    }
    var replay = pause(task, operation, "0");
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay).path("outcome").asText()).isEqualTo("REPLAY");
    assertThat(body(replay).path("task").path("status").asText()).isEqualTo("PAUSED");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM task_pause_operations WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(1);
  }

  @Test
  void TC404_pauseCommittedBeforeConcurrentClaimNeverGrantsExecution() throws Exception {
    var task = create();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new Gate()) {
      probe.on(
          EXECUTION,
          "claim",
          (method, args, real) -> {
            gate.pause();
            return real.run();
          });
      var claim = executor.submit(() -> claim(task, "concurrent-claim"));
      try {
        gate.await();
        assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
      } finally {
        gate.close();
      }
      assertThat(property(claim.get(30, TimeUnit.SECONDS), "kind").toString())
          .isNotEqualTo("ACQUIRED");
      assertThat(state(task)).isEqualTo("PAUSED");
    } finally {
      probe.clear();
    }
  }

  @Test
  void TC404_pendingPauseClosesOriginalCommandAndSameOperationReplaysWithoutVersionChange()
      throws Exception {
    var task = create();
    var operation = UUID.randomUUID();
    var first = pause(task, operation, "0");
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(body(first).path("task").path("status").asText()).isEqualTo("PAUSED");
    assertThat(state(task)).isEqualTo("PAUSED");
    assertThat(outbox(task))
        .containsEntry("status", "CLOSED")
        .containsEntry("close_reason", "USER_PAUSED");
    long version =
        db.queryForObject(
            "SELECT version FROM export_tasks WHERE task_id=?", Long.class, task.toString());
    var replay = pause(task, operation, "0");
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay).path("outcome").asText()).isEqualTo("REPLAY");
    assertThat(
            db.queryForObject(
                "SELECT version FROM export_tasks WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(version);
    assertThat(property(claim(task, "late-owner"), "kind").toString()).isNotEqualTo("ACQUIRED");
  }

  @Test
  void TC404_runningPauseRequiresProofAndFencesOldBusinessWrites() throws Exception {
    var task = create();
    var lease = lease(task);
    collect(task, lease);
    var response = pause(task, UUID.randomUUID(), "0");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(state(task)).isEqualTo("PAUSING");
    assertThat(port(EXECUTION, "renew", lease).toString()).isEqualTo("STALE");
    assertThat(port(EXECUTION, "complete", lease, failure("SYNTHETIC_FAILURE")).toString())
        .isEqualTo("STALE");
    long checkpoint =
        db.queryForObject(
            "SELECT checkpoint_version FROM export_checkpoints WHERE task_id=?",
            Long.class,
            task.toString());
    long control = body(response).path("task").path("controlVersion").asLong();
    Class<?> proofType = method(EXECUTION, "acknowledgePause").getParameterTypes()[0];
    var proof =
        record(
            proofType,
            Map.of(
                "taskId",
                task,
                "owner",
                property(lease, "owner"),
                "fence",
                property(lease, "fence"),
                "requestedControlVersion",
                control,
                "lastCheckpointVersion",
                checkpoint));
    port(EXECUTION, "acknowledgePause", proof);
    assertThat(state(task)).isEqualTo("PAUSED");
    assertThat(
            db.queryForObject(
                "SELECT finished_at FROM export_tasks WHERE task_id=?",
                Object.class,
                task.toString()))
        .isNull();
    assertThat(outbox(task)).containsEntry("close_reason", "USER_PAUSED");
  }

  @Test
  void TC407_sameOperationWithDifferentPayloadIsRejectedAndControlVersionIsIndependent()
      throws Exception {
    var task = create();
    var operation = UUID.randomUUID();
    db.update("UPDATE export_tasks SET version=version+100 WHERE task_id=?", task.toString());
    assertThat(pause(task, operation, "0").statusCode()).isEqualTo(200);
    var conflict = pause(task, operation, "1");
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(body(conflict).path("code").asText()).isEqualTo("OPERATION_ID_REUSED");
    var other = create();
    var reused = pause(other, operation, "0");
    assertThat(reused.statusCode()).isEqualTo(409);
    assertThat(state(other)).isEqualTo("PENDING");
  }

  @Test
  void TC404_completionFirstWinsAndPauseDoesNotOverwriteSuccess() throws Exception {
    var task = create();
    var lease = lease(task);
    var result = generate(task, lease);
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("APPLIED");
    var response = pause(task, UUID.randomUUID(), "0");
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(body(response).path("code").asText()).isEqualTo("TASK_ACTION_NOT_ALLOWED");
    assertThat(state(task)).isEqualTo("SUCCEEDED");
  }

  @Test
  void TC407_invalidPauseInputsDoNotAlterTaskOrOriginalCommand() throws Exception {
    var task = create();
    var before = outbox(task);
    for (var payload :
        List.of(
            "{}",
            "{\"operationId\":\"invalid\",\"expectedControlVersion\":\"0\"}",
            "{\"operationId\":\"" + UUID.randomUUID() + "\",\"expectedControlVersion\":\"-1\"}",
            "{\"operationId\":\""
                + UUID.randomUUID()
                + "\",\"expectedControlVersion\":\"0\",\"extra\":true}")) {
      var response = pauseBody(task, payload);
      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(body(response).path("outcome").asText()).isEqualTo("REJECTED");
    }
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(outbox(task)).isEqualTo(before);
  }
}
