// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.testing.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
// [SC-005](../../../../../../../docs/product/acceptance-criteria.md#sc-005)
class DurabilityGuardTest extends BoundaryFixture {
  @Test
  void TC235_oldCheckpointVersionAndOldFenceCannotWriteBlocksOrProgress() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var old = lease(task);
    var args = new AtomicReference<Object[]>();
    probe.on(
        CHECKPOINT,
        "commitBlock",
        (m, a, real) -> {
          args.compareAndSet(null, a.clone());
          return real.run();
        });
    var ready = generate(task, old);
    assertThat(args.get()).isNotNull();
    probe.clear();
    var checkpoint = port(CHECKPOINT, "find", task);
    var progress =
        db.queryForMap(
            "SELECT processed_count,status FROM export_tasks WHERE task_id=?", task.toString());
    var blocks =
        db.queryForList(
            "SELECT * FROM export_data_blocks WHERE task_id=? ORDER BY sequence", task.toString());
    // 同owner的首块前序版本已过期，即使身份仍有效也不能覆盖后续进度。
    assertThat(property(port(CHECKPOINT, "commitBlock", args.get()), "kind").toString())
        .isEqualTo("STALE");
    expireExecution(task);
    var next = property(claim(task, "synthetic-next-owner"), "lease");
    assertThat(
            property(port(CHECKPOINT, "commitBlock", old, checkpoint, args.get()[2]), "kind")
                .toString())
        .isEqualTo("STALE");
    assertThat(
            db.queryForList(
                "SELECT * FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
                task.toString()))
        .isEqualTo(blocks);
    assertThat(
            db.queryForMap(
                "SELECT processed_count,status FROM export_tasks WHERE task_id=?", task.toString()))
        .isEqualTo(progress);
    assertThat(call(EXEC_SERVICE, "complete", old, failure("EXPORT_CHECKPOINT_INVALID")).toString())
        .isEqualTo("STALE");
    assertThat(call(EXEC_SERVICE, "complete", next, generate(task, next)).toString())
        .isEqualTo("APPLIED");
  }

  @Test
  void TC235_noTrustedPrefixWithoutSafeRedoProofCannotStartNewGeneration() throws Exception {
    var task = create();
    var lease = lease(task);
    generate(task, lease);
    var checkpoint = port(CHECKPOINT, "find", task);
    Class<?> type = method(CHECKPOINT, "startGeneration").getParameterTypes()[2];
    var prefix =
        record(
            type,
            Map.of(
                "generation",
                property(checkpoint, "generation"),
                "version",
                property(checkpoint, "version"),
                "blocks",
                List.of(),
                "verifiedCount",
                0L,
                "isSafeToRedo",
                false));
    try {
      assertThat(
              property(
                      port(
                          CHECKPOINT,
                          "startGeneration",
                          lease,
                          property(checkpoint, "version"),
                          prefix),
                      "kind")
                  .toString())
          .isNotEqualTo("APPLIED");
    } catch (java.lang.reflect.InvocationTargetException invalid) {
      assertThat(invalid.getCause()).isNotNull();
    }
    assertThat(port(CHECKPOINT, "find", task)).isEqualTo(checkpoint);
  }

  @Test
  void TC235_readyCommitResponseLostReconcilesSameProofWithoutRegeneration() throws Exception {
    var task = create();
    var lease = lease(task);
    var lost = new AtomicBoolean();
    probe.on(
        ARTIFACT,
        "registerReady",
        (m, a, real) -> {
          Object result = real.run();
          if (lost.compareAndSet(false, true))
            throw new java.sql.SQLRecoverableException("synthetic READY response lost");
          return result;
        });
    var result = generate(task, lease);
    assertThat(lost).isTrue();
    var proof =
        db.queryForMap(
            "SELECT artifact_id,checksum FROM export_artifacts WHERE task_id=?", task.toString());
    int writes = probe.calls(STORAGE, "writeBlock"), publishes = probe.calls(STORAGE, "publish");
    probe.clear();
    if (property(result, "kind").toString().equals("UNKNOWN")) {
      expireExecution(task);
      lease = property(claim(task, "synthetic-reconciler"), "lease");
      result = generate(task, lease);
    }
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(call(EXEC_SERVICE, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForMap(
                "SELECT artifact_id,checksum FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                task.toString()))
        .isEqualTo(proof);
    assertThat(probe.calls(STORAGE, "writeBlock")).isEqualTo(writes);
    assertThat(probe.calls(STORAGE, "publish")).isEqualTo(publishes);
  }

  @Test
  void TC224_budgetExhaustionCommitsSafeFailureAndHighWaterBeforeAck() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    probe.on(
        STORAGE,
        "readBlock",
        (m, a, real) -> {
          throw new java.nio.file.AccessDeniedException("synthetic pause before assembly");
        });
    assertThat(property(generate(task, lease), "kind").toString()).isEqualTo("UNKNOWN");
    probe.clear();
    long processed =
        db.queryForObject(
            "SELECT processed_count FROM export_tasks WHERE task_id=?",
            Long.class,
            task.toString());
    assertThat(processed).isPositive();
    db.update("UPDATE task_execution SET recovery_count=3 WHERE task_id=?", task.toString());
    expireExecution(task);
    var channel = mock(com.rabbitmq.client.Channel.class);
    var seen = new AtomicBoolean();
    probe.on(
        EXECUTION,
        "claim",
        (m, a, real) -> {
          noAck(channel);
          Object result = real.run();
          assertThat(state(task)).isEqualTo("FAILED");
          assertThat(
                  db.queryForObject(
                      "SELECT error_code FROM export_tasks WHERE task_id=?",
                      String.class,
                      task.toString()))
              .isEqualTo("EXPORT_RECOVERY_EXHAUSTED");
          assertThat(
                  db.queryForObject(
                      "SELECT processed_count FROM export_tasks WHERE task_id=?",
                      Long.class,
                      task.toString()))
              .isEqualTo(processed);
          assertThat(
                  db.queryForObject(
                      "SELECT finished_at FROM export_tasks WHERE task_id=?",
                      java.sql.Timestamp.class,
                      task.toString()))
              .isNotNull();
          seen.set(true);
          return result;
        });
    call(
        "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener",
        "onMessage",
        command(task),
        channel,
        81L);
    assertThat(seen).isTrue();
    verify(channel).basicAck(81L, false);
    assertThat(probe.calls(GENERATOR, "generate")).isEqualTo(1);
  }

  @Test
  void TC235_rebuildRetainsHighWaterAndReadsFromTrustedCursorRatherThanHighWater()
      throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    probe.on(
        STORAGE,
        "readBlock",
        (m, a, real) -> {
          throw new java.nio.file.AccessDeniedException("synthetic before assembly");
        });
    generate(task, lease);
    probe.clear();
    long high =
        db.queryForObject(
            "SELECT processed_count FROM export_tasks WHERE task_id=?",
            Long.class,
            task.toString());
    assertThat(high).isEqualTo(2501);
    db.update(
        "UPDATE export_data_blocks SET checksum=? WHERE task_id=? AND sequence=(SELECT maximum_sequence FROM (SELECT MAX(sequence) maximum_sequence FROM export_data_blocks WHERE task_id=?) x)",
        "0".repeat(64),
        task.toString(),
        task.toString());
    var read = new AtomicBoolean();
    probe.on(
        ORDERS,
        "readBatch",
        (m, a, real) -> {
          read.set(true);
          assertThat(
                  db.queryForObject(
                      "SELECT processed_count FROM export_tasks WHERE task_id=?",
                      Long.class,
                      task.toString()))
              .isGreaterThanOrEqualTo(high);
          return real.run();
        });
    expireExecution(task);
    var next = property(claim(task, "synthetic-rebuild"), "lease");
    var result = generate(task, next);
    assertThat(read).isTrue();
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(call(EXEC_SERVICE, "complete", next, result).toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForObject(
                "SELECT processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(2501);
  }
}
