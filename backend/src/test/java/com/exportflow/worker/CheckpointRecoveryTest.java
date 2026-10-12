// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-010](../../../../../../../docs/product/acceptance-criteria.md#ac-010)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
class CheckpointRecoveryTest extends ProductionBackendFixture {
  @Test
  void TC235_killAfterCommittedBlockResumesOriginalCommandAndChoosesOneResult() throws Exception {
    useOrderVolume(110000);
    var task = createVolume();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    var original = startWorker(true);
    try {
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(original.isAlive()).isTrue();
                assertThat(root.resolve("block-committed.marker")).exists();
              });
      long prefix =
          db.queryForObject(
              "SELECT processed_count FROM export_tasks WHERE task_id=?",
              Long.class,
              task.toString());
      assertThat(prefix).isPositive().isLessThan(110000);
      long generation =
          db.queryForObject(
              "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
              Long.class,
              task.toString());
      var trusted =
          db.queryForList(
              "SELECT block_id,checksum FROM export_data_blocks WHERE task_id=? AND data_generation=? ORDER BY sequence",
              task.toString(),
              generation);
      assertThat(trusted).isNotEmpty();
      original.destroyForcibly();
      assertThat(original.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      db.update(
          "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      var resumed = startWorker(false);
      try {
        awaitSuccess(task);
      } finally {
        stopWorker(resumed);
      }
      assertThat(
              db.queryForObject(
                  "SELECT processed_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(110000);
      assertThat(
              db.queryForObject(
                  "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(generation);
      var resumedBlocks =
          db.queryForList(
              "SELECT block_id,checksum FROM export_data_blocks WHERE task_id=? AND data_generation=? ORDER BY sequence",
              task.toString(),
              generation);
      assertThat(resumedBlocks.subList(0, trusted.size()))
          .as("可信前缀必须原引用续写，不能删除后全量重建掩盖恢复缺失")
          .isEqualTo(trusted);
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM command_outbox WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                  Long.class,
                  task.toString()))
          .isEqualTo(1);
    } finally {
      if (original.isAlive()) stopWorker(original);
    }
  }

  @Test
  void TC235_recoveryBudgetPersistsAndOnlySuccessfulTakeoversConsumeIt() throws Exception {
    var task = create();
    String service = "com.exportflow.worker.application.TaskExecutionService";
    call(service, "claim", task, "synthetic-original");
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isZero();
    call(service, "claim", task, "synthetic-busy");
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isZero();
    for (int attempt = 1; attempt <= 3; attempt++) {
      db.update(
          "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      call(service, "claim", task, "synthetic-recovery-" + attempt);
      assertThat(
              db.queryForObject(
                  "SELECT recovery_count FROM task_execution WHERE task_id=?",
                  Integer.class,
                  task.toString()))
          .isEqualTo(attempt);
    }
    db.update(
        "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    call(service, "claim", task, "synthetic-exhausted");
    assertThat(state(task)).isEqualTo("FAILED");
    assertThat(
            db.queryForObject(
                "SELECT finished_at FROM export_tasks WHERE task_id=?",
                java.sql.Timestamp.class,
                task.toString()))
        .isNotNull();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM command_outbox WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(1);
  }
}
