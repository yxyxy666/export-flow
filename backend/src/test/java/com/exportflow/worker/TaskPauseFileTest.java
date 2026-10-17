// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.dispatch.infrastructure.messaging.ExportCommandCodec;
import com.exportflow.testing.TaskManagementFixture;
import com.exportflow.worker.domain.ExecutionModels.DeliveryContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

// [AC-018](../../../../../../../docs/product/acceptance-criteria.md#ac-018)
// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
// [AC-083](../../../../../../../docs/product/acceptance-criteria.md#ac-083)
class TaskPauseFileTest extends TaskManagementFixture {
  @Test
  void TC405_pauseWithoutClosedResourceProofCannotAckOrClaimStopped() throws Exception {
    var task = create();
    var delivery = new DeliveryContext("pause-unclosed", 1L, Instant.now());
    call("com.exportflow.worker.application.ExecutionSupervisor", "onDelivery", delivery, task);
    probe.on(
        GENERATOR,
        "generate",
        (method, args, real) -> {
          var result = real.run();
          assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
          return copy(result, Map.of("resourcesClosed", false));
        });
    try {
      var envelope = app.getBean(ExportCommandCodec.class).decode(command(task));
      assertThat(
              call(
                      "com.exportflow.worker.application.ExportCommandHandler",
                      "handle",
                      envelope,
                      delivery)
                  .toString())
          .isEqualTo("HOLD");
      assertThat(state(task)).isEqualTo("PAUSING");
    } finally {
      probe.clear();
      call("com.exportflow.worker.application.ExecutionSupervisor", "finish", delivery.channelId());
    }
  }

  @Test
  void TC405_userPauseStopsGuardButKeepsDeliveryChannelForSafeReceipt() throws Exception {
    var task = create();
    var lease = lease(task);
    var delivery = new DeliveryContext("pause-heartbeat", 2L, Instant.now());
    call("com.exportflow.worker.application.ExecutionSupervisor", "onDelivery", delivery, task);
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    call(
        "com.exportflow.worker.application.ExecutionSupervisor",
        "attachChannel",
        delivery.channelId(),
        (Runnable) () -> closed.set(true));
    var guard =
        call(
            "com.exportflow.worker.application.ExecutionSupervisor",
            "onExecution",
            delivery,
            lease);
    try {
      assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
      call("com.exportflow.worker.application.ExecutionSupervisor", "heartbeat", lease);
      assertThat(closed).isFalse();
      assertThat(((com.exportflow.worker.port.ExecutionGuard) guard).check()).isFalse();
    } finally {
      call("com.exportflow.worker.application.ExecutionSupervisor", "finish", delivery.channelId());
    }
  }

  @Test
  void TC405_pauseAfterGeneratorReturnAcknowledgesOnlyClosedResourcesAndPersistentPause()
      throws Exception {
    var task = create();
    var delivery = new DeliveryContext("pause-at-return", 1L, Instant.now());
    call("com.exportflow.worker.application.ExecutionSupervisor", "onDelivery", delivery, task);
    probe.on(
        GENERATOR,
        "generate",
        (method, args, real) -> {
          var result = real.run();
          assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
          return result;
        });
    try {
      var envelope = app.getBean(ExportCommandCodec.class).decode(command(task));
      var decision =
          call(
              "com.exportflow.worker.application.ExportCommandHandler",
              "handle",
              envelope,
              delivery);
      assertThat(decision.toString()).isEqualTo("ACK");
      assertThat(state(task)).isEqualTo("PAUSED");
      assertThat(outbox(task)).containsEntry("close_reason", "USER_PAUSED");
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                  Long.class,
                  task.toString()))
          .isZero();
    } finally {
      probe.clear();
      call("com.exportflow.worker.application.ExecutionSupervisor", "finish", delivery.channelId());
    }
  }

  @Test
  void TC405_pauseAfterReadyPreservesReferencedFilesAndRejectsLateSuccess() throws Exception {
    var task = create();
    var lease = lease(task);
    var generated = generate(task, lease);
    var checkpoint = port(CHECKPOINT, "find", task);
    var blocks = (List<?>) property(checkpoint, "blocks");
    var response = pause(task, UUID.randomUUID(), "0");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(state(task)).isEqualTo("PAUSING");
    assertThat(port(EXECUTION, "complete", lease, generated).toString()).isEqualTo("STALE");
    for (var block : blocks)
      assertThat((Path) port(STORAGE, "resolve", property(block, "storageRef"))).exists();
    var artifact = property(generated, "artifact");
    assertThat((Path) port(STORAGE, "resolve", property(artifact, "artifactRef"))).exists();
    assertThat(
            db.queryForObject(
                "SELECT state FROM export_artifacts WHERE artifact_id=?",
                String.class,
                property(artifact, "artifactId").toString()))
        .isEqualTo("READY");
  }

  @Test
  void TC405_pausedOriginalDeliveryAcknowledgesAfterRestartWithoutGenerating() throws Exception {
    var task = create();
    assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
    var envelope = app.getBean(ExportCommandCodec.class).decode(command(task));
    restartApplication();
    var decision =
        call(
            "com.exportflow.worker.application.ExportCommandHandler",
            "handle",
            envelope,
            new DeliveryContext("paused-test-channel", 1L, Instant.now()));
    assertThat(decision.toString()).isEqualTo("ACK");
    assertThat(state(task)).isEqualTo("PAUSED");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM command_outbox WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(1);
  }
}
