// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-052](../../../../../../../docs/product/acceptance-criteria.md#ac-052)
// [AC-053](../../../../../../../docs/product/acceptance-criteria.md#ac-053)
class OutboxDeadlineRaceTest extends BoundaryFixture {
  @ParameterizedTest
  @ValueSource(strings = {"SENT", "CLAIM"})
  void TC222_timeoutWinsAgainstLateSentOrClaim(String operation) throws Exception {
    var task = create();
    var reserved = reserve(task);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new BoundaryProbe.Gate()) {
      String port = operation.equals("SENT") ? OUTBOX : EXECUTION;
      String method = operation.equals("SENT") ? "markSent" : "claim";
      probe.on(
          port,
          method,
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      var late =
          pool.submit(
              () ->
                  operation.equals("SENT")
                      ? port(OUTBOX, "markSent", reserved, evidence())
                      : claim(task, "synthetic-late"));
      gate.await();
      db.update(
          "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      call(TIMEOUT, "scanExpired");
      assertThat(state(task)).isEqualTo("FAILED");
      assertThat(
              port(
                  OUTBOX,
                  "scheduleRetry",
                  reserved,
                  "SYNTHETIC_LATE",
                  java.time.Duration.ofSeconds(1)))
          .isEqualTo(false);
      gate.close();
      Object result = late.get(20, TimeUnit.SECONDS);
      if (operation.equals("SENT")) assertThat(result).isEqualTo(false);
      else assertThat(BoundaryProbe.property(result, "kind").toString()).isNotEqualTo("ACQUIRED");
    }
    assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
    assertThat(outbox(task).get("lease_token")).isNull();
    assertThat(count("export_artifacts")).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"SENT", "CLAIM"})
  void TC222_committedSentOrClaimWinsAgainstTimeout(String operation) throws Exception {
    var task = create();
    var reserved = reserve(task);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new BoundaryProbe.Gate()) {
      probe.on(
          OUTBOX,
          "expireOrClose",
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      var scan = pool.submit(() -> port(OUTBOX, "expireOrClose", task));
      gate.await();
      if (operation.equals("SENT"))
        assertThat(port(OUTBOX, "markSent", reserved, evidence())).isEqualTo(true);
      else
        assertThat(BoundaryProbe.property(claim(task, "synthetic-early"), "kind").toString())
            .isEqualTo("ACQUIRED");
      db.update(
          "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      gate.close();
      scan.get(20, TimeUnit.SECONDS);
    }
    assertThat(state(task)).isEqualTo(operation.equals("SENT") ? "PENDING" : "RUNNING");
    if (operation.equals("CLAIM")) {
      assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
      assertThat(outbox(task).get("close_reason")).isEqualTo("AUTHORITY_NO_LONGER_NEEDS_COMMAND");
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @ValueSource(strings = {"READY", "KNOWN_FAILURE"})
  void TC222_unsentTerminalClosesOriginalOutboxWithoutAnyPublish(String terminal) throws Exception {
    var task = create();
    var lease = lease(task);
    var result = terminal.equals("READY") ? generate(task, lease) : failure("EXPORT_FILE_INVALID");
    assertThat(call(EXEC_SERVICE, "complete", lease, result).toString()).isEqualTo("APPLIED");
    String authority = state(task);
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    call(TIMEOUT, "scanExpired");
    call(DISPATCH, "scanAvailable");
    assertThat(state(task)).isEqualTo(authority);
    assertThat(outbox(task).get("status")).isEqualTo("CLOSED");
    assertThat(probe.calls(PUBLISHER, "publish")).isZero();
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNull();
    }
  }
}
