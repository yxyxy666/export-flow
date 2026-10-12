// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-054](../../../../../../../docs/product/acceptance-criteria.md#ac-054)
// [AC-055](../../../../../../../docs/product/acceptance-criteria.md#ac-055)
// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [SC-010](../../../../../../../docs/product/acceptance-criteria.md#sc-010)
// 等待计数为[运行观察设计](../../../../../../../specs/002-order-management/decision-record.md)的工程辅助回归。
class OutboxDispatchMySqlTest extends ProductionBackendFixture {
  @Test
  void TC221_startedTasksAtHeadCannotStarveNewPendingCommand() throws Exception {
    for (int index = 0; index < 2; index++) {
      var old = create();
      call(
          "com.exportflow.worker.application.TaskExecutionService",
          "claim",
          old,
          "candidate-owner-" + index);
      db.update(
          "UPDATE command_outbox SET next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
          old.toString());
      assertThat(state(old)).isEqualTo("RUNNING");
    }
    var pending = create();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?",
                String.class,
                pending.toString()))
        .isEqualTo("SENT");
    assertThat(state(pending)).isEqualTo("PENDING");
  }

  @Test
  void TC221_waitingAlarmCountsOnlyPendingTasksThatStillNeedCommand() throws Exception {
    var started = create();
    call("com.exportflow.worker.application.TaskExecutionService", "claim", started, "alarm-owner");
    var pending = create();
    db.update(
        "UPDATE command_outbox SET accepted_at=UTC_TIMESTAMP()-INTERVAL 2 MINUTE WHERE task_id IN (?,?)",
        started.toString(),
        pending.toString());
    assertThat(app.getBean(com.exportflow.config.RuntimeHealthMapper.class).counts().waitingCount())
        .isEqualTo(1);
  }

  @Test
  void TC221_unsupportedRecordDoesNotRollbackOtherPublishedCommandAndKeepsHistory()
      throws Exception {
    var bad = create();
    var good = create();
    db.update("UPDATE command_outbox SET schema_version=999 WHERE task_id=?", bad.toString());
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, good.toString()))
        .isEqualTo("SENT");
    var badCommand =
        db.queryForMap(
            "SELECT status,attempt_no,next_attempt_at,last_error_code FROM command_outbox WHERE task_id=?",
            bad.toString());
    assertThat(badCommand.get("status")).isNotEqualTo("SENT");
    assertThat(badCommand.get("last_error_code")).isNotNull();
    assertThat(((Number) badCommand.get("attempt_no")).longValue()).isPositive();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM dispatch_attempts a JOIN command_outbox o ON a.event_id=o.event_id WHERE o.task_id=?",
                Long.class,
                bad.toString()))
        .isPositive();
  }
}
