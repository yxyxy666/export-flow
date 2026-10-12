// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

// [AC-052](../../../../../../../docs/product/acceptance-criteria.md#ac-052)
class DispatchSchedulingTest extends BoundaryFixture {
  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @Test
  void TC222_realFiveSecondIndependentScanWorksEvenWhilePublisherIsBlocked() throws Exception {
    try (var gate = new BoundaryProbe.Gate()) {
      probe.on(
          PUBLISHER,
          "publish",
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      var expiring = create();
      gate.await();
      db.update(
          "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_attempt_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE task_id=?",
          expiring.toString());
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(7))
          .untilAsserted(() -> assertThat(state(expiring)).isEqualTo("FAILED"));
      assertThat(outbox(expiring).get("status")).isEqualTo("CLOSED");
    }
  }

  @Test
  void TC222_restartScansAlreadyExpiredBeforePublishingWithoutResettingDeadline() throws Exception {
    call("com.exportflow.dispatch.application.DispatchScheduler", "stop", Duration.ofSeconds(5));
    var task = create();
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    Object deadline = outbox(task).get("dispatch_deadline");
    probe.on(
        PUBLISHER,
        "publish",
        (m, a, real) -> {
          throw new AssertionError("已过期原命令不能在启动恢复时先发布");
        });
    restartApplication();
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(state(task)).isEqualTo("FAILED"));
    assertThat(outbox(task).get("dispatch_deadline")).isEqualTo(deadline);
    assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
  }
}
