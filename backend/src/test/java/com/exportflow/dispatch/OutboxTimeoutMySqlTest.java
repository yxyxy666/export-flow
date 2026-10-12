// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.BoundaryFixture;
import org.junit.jupiter.api.Test;

// [AC-052](../../../../../../../docs/product/acceptance-criteria.md#ac-052)
// [AC-053](../../../../../../../docs/product/acceptance-criteria.md#ac-053)
class OutboxTimeoutMySqlTest extends BoundaryFixture {
  @Test
  void TC222_acceptedWindowIsPersistedAsTenMinutesAndBeforeDeadlineDoesNotFail() throws Exception {
    var task = create();
    assertThat(
            db.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND,accepted_at,dispatch_deadline) FROM command_outbox WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(600);
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(task)).isEqualTo("PENDING");
  }

  @Test
  void TC222_runningTaskCannotBeChangedToDispatchTimeoutFailure() throws Exception {
    var task = create();
    call(
        "com.exportflow.worker.application.TaskExecutionService", "claim", task, "synthetic-owner");
    assertThat(state(task)).isEqualTo("RUNNING");
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC222_expiredPendingFailsAndClosesDespiteLiveLeaseOrLateBackoff() throws Exception {
    var task = create();
    reserve(task);
    assertThat(outbox(task).get("lease_token")).isNotNull();
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND,lease_until=UTC_TIMESTAMP()+INTERVAL 1 MINUTE, next_attempt_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(task)).isEqualTo("FAILED");
    assertThat(
            db.queryForObject(
                "SELECT error_code FROM export_tasks WHERE task_id=?",
                String.class,
                task.toString()))
        .isEqualTo("TASK_DISPATCH_TIMEOUT");
    var outbox =
        db.queryForMap(
            "SELECT status,lease_token,closed_at FROM command_outbox WHERE task_id=?",
            task.toString());
    assertThat(outbox.get("status")).isEqualTo("CLOSED");
    assertThat(outbox.get("lease_token")).isNull();
    assertThat(outbox.get("closed_at")).isNotNull();
  }

  @Test
  void TC222_sentPendingIsNotFailedOrRepublishedBecauseItWaitedTooLong() throws Exception {
    var task = create();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task.toString()))
        .isEqualTo("SENT");
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task.toString()))
        .isEqualTo("SENT");
    call(DISPATCH, "scanAvailable");
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNotNull();
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNull();
    }
  }
}
