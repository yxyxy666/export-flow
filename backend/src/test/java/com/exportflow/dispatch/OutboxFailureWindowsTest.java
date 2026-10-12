// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-053](../../../../../../../docs/product/acceptance-criteria.md#ac-053)
// [AC-054](../../../../../../../docs/product/acceptance-criteria.md#ac-054)
// [AC-055](../../../../../../../docs/product/acceptance-criteria.md#ac-055)
// [SC-010](../../../../../../../docs/product/acceptance-criteria.md#sc-010)
class OutboxFailureWindowsTest extends BoundaryFixture {
  @Test
  void TC221_claimCrashKeepsAttemptAndRequiresBothLeaseAndCrashBackoff() throws Exception {
    var task = create();
    var old = reserve(task);
    var before = outbox(task);
    var history =
        db.queryForList(
            "SELECT * FROM dispatch_attempts WHERE event_id=?",
            property(old, "eventId").toString());
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().get("crash_retry_at")).isNotNull();
    assertThat(before.get("attempt_no")).isEqualTo(1);
    db.update(
        "UPDATE command_outbox SET lease_until=UTC_TIMESTAMP()+INTERVAL 30 SECOND,next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    call(DISPATCH, "scanAvailable");
    assertThat(outbox(task).get("attempt_no")).isEqualTo(before.get("attempt_no"));
    db.update(
        "UPDATE command_outbox SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_attempt_at=UTC_TIMESTAMP()+INTERVAL 30 SECOND WHERE task_id=?",
        task.toString());
    call(DISPATCH, "scanAvailable");
    assertThat(outbox(task).get("attempt_no")).isEqualTo(before.get("attempt_no"));
    db.update(
        "UPDATE command_outbox SET next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    var next = reserve(task);
    assertThat(property(next, "eventId")).isEqualTo(property(old, "eventId"));
    assertThat(property(next, "token")).isNotEqualTo(property(old, "token"));
    assertThat(outbox(task).get("dispatch_deadline")).isEqualTo(before.get("dispatch_deadline"));
    assertThat(
            db.queryForList(
                "SELECT * FROM dispatch_attempts WHERE event_id=? ORDER BY attempt_no",
                property(old, "eventId").toString()))
        .hasSize(2);
    assertThat(port(OUTBOX, "markSent", old, evidence())).isEqualTo(false);
    assertThat(port(OUTBOX, "scheduleRetry", old, "SYNTHETIC_LATE", Duration.ofSeconds(1)))
        .isEqualTo(false);
    assertThat(outbox(task).get("lease_token").toString())
        .isEqualTo(property(next, "token").toString());
  }

  @Test
  void TC221_twoDispatchersPublishOriginalCommandOnce() throws Exception {
    var task = create();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var gate = new CyclicBarrier(2);
      var results = new ArrayList<Future<?>>();
      for (int i = 0; i < 2; i++)
        results.add(
            threads.submit(
                () -> {
                  gate.await();
                  return call(DISPATCH, "scanAvailable");
                }));
      for (var result : results) result.get(20, TimeUnit.SECONDS);
    }
    assertThat(outbox(task).get("status")).isEqualTo("SENT");
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNotNull();
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNull();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void TC221_confirmedPublishWithLostSentWriteNeverImmediatelyRepublishes(boolean unreadable)
      throws Exception {
    var task = create();
    var failed = new AtomicBoolean();
    var published = new AtomicInteger();
    probe.on(
        PUBLISHER,
        "publish",
        (m, a, real) -> {
          published.incrementAndGet();
          return real.run();
        });
    probe.on(
        OUTBOX,
        "markSent",
        (m, a, real) -> {
          Object result = real.run();
          if (failed.compareAndSet(false, true))
            throw new java.sql.SQLRecoverableException("synthetic SENT commit ack lost");
          return result;
        });
    if (unreadable)
      probe.on(
          OUTBOX,
          "find",
          (m, a, real) -> {
            if (failed.get())
              throw new java.sql.SQLTransientConnectionException("synthetic reread unavailable");
            return real.run();
          });
    try {
      call(DISPATCH, "scanAvailable");
    } catch (java.lang.reflect.InvocationTargetException expected) {
      assertThat(failed).isTrue();
    }
    assertThat(failed).isTrue();
    assertThat(published).hasValue(1);
    assertThat(outbox(task).get("status")).isEqualTo("SENT");
    probe.clear();
    call(DISPATCH, "scanAvailable");
    assertThat(probe.calls(PUBLISHER, "publish")).isEqualTo(1);
  }

  @Test
  void TC221_failedSentWriteBeforeCommitRetriesMarkWithEvidenceInsteadOfPublish() throws Exception {
    var task = create();
    var failed = new AtomicBoolean();
    probe.on(
        OUTBOX,
        "markSent",
        (m, a, real) -> {
          if (failed.compareAndSet(false, true))
            throw new java.sql.SQLTransientConnectionException("synthetic before mark");
          return real.run();
        });
    call(DISPATCH, "scanAvailable");
    assertThat(failed).isTrue();
    assertThat(outbox(task).get("status")).isEqualTo("SENT");
    assertThat(probe.calls(PUBLISHER, "publish")).isEqualTo(1);
    assertThat(probe.calls(OUTBOX, "markSent")).isGreaterThanOrEqualTo(2);
  }

  @Test
  void TC221_brokerUnavailableRetainsTaskAndOriginalCommandThenRecoveryDelivers() throws Exception {
    var task = create();
    var original = outbox(task);
    rabbit.suspendConnections();
    try {
      call(DISPATCH, "scanAvailable");
    } catch (java.lang.reflect.InvocationTargetException classified) {
      /* 共享依赖故障可以有界结束本批。 */
    }
    assertThat(outbox(task).get("status")).isNotEqualTo("SENT");
    assertThat(state(task)).isEqualTo("PENDING");
    rabbit.resumeConnections();
    db.update(
        "UPDATE command_outbox SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    call(DISPATCH, "scanAvailable");
    assertThat(outbox(task).get("status")).isEqualTo("SENT");
    assertThat(outbox(task).get("event_id")).isEqualTo(original.get("event_id"));
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @Test
  void TC221_singleSerializationFailureIsIsolatedFromGoodRecord() throws Exception {
    var bad = create();
    var good = create();
    probe.on(
        PUBLISHER,
        "publish",
        (m, a, real) -> {
          if (property(a[0], "taskId").equals(bad))
            throw new IllegalArgumentException("synthetic unsupported payload");
          return real.run();
        });
    call(DISPATCH, "scanAvailable");
    assertThat(outbox(good).get("status")).isEqualTo("SENT");
    assertThat(outbox(bad).get("status")).isNotEqualTo("SENT");
    assertThat(outbox(bad).get("last_error_code")).isNotNull();
    assertThat(count("dispatch_attempts")).isEqualTo(2);
  }

  @Test
  void TC221_sharedBrokerFailureStopsBatchAndUnreadableDatabaseDoesNotWriteInventedState()
      throws Exception {
    for (int i = 0; i < 12; i++) create();
    var reached = new AtomicInteger();
    rabbit.suspendConnections();
    probe.on(
        PUBLISHER,
        "publish",
        (m, a, real) -> {
          reached.incrementAndGet();
          return real.run();
        });
    var configured = new AtomicReference<Object>();
    probe.on(
        OUTBOX,
        "claimDue",
        (m, a, real) -> {
          configured.set(a[2]);
          return real.run();
        });
    long started = System.nanoTime();
    try {
      call(DISPATCH, "scanAvailable");
    } catch (java.lang.reflect.InvocationTargetException controlled) {
    }
    Object policy = configured.get();
    assertThat(policy).isNotNull();
    int maxConcurrency = ((Number) property(policy, "maxConcurrency")).intValue();
    Duration budget = (Duration) property(policy, "publishBudget");
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isLessThan(budget.multipliedBy(2).plusSeconds(2));
    assertThat(reached.get()).isBetween(1, maxConcurrency);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM command_outbox WHERE status='SENT'", Long.class))
        .isZero();
    probe.clear();
    var before = db.queryForList("SELECT * FROM command_outbox ORDER BY event_id");
    probe.on(
        OUTBOX,
        "claimDue",
        (m, a, real) -> {
          throw new java.sql.SQLTransientConnectionException("synthetic DB unreadable");
        });
    try {
      call(DISPATCH, "scanAvailable");
    } catch (java.lang.reflect.InvocationTargetException controlled) {
    }
    assertThat(db.queryForList("SELECT * FROM command_outbox ORDER BY event_id")).isEqualTo(before);
  }

  @Test
  void TC221_realProcessKillAfterClaimPreservesAttemptAndCrashBackoffAcrossRestart()
      throws Exception {
    var task = create();
    var child =
        startWorker(
            List.of(
                "--test-barrier-port=" + OUTBOX,
                "--test-barrier-method=claimDue",
                "--test-barrier-phase=after",
                "--test-barrier-marker=claim.marker"));
    try {
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(child.isAlive()).isTrue();
                assertThat(root.resolve("claim.marker")).exists();
              });
      var reserved = outbox(task);
      assertThat(reserved.get("attempt_no")).isEqualTo(1);
      assertThat(count("dispatch_attempts")).isEqualTo(1);
      child.destroyForcibly();
      assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
      restartApplication();
      db.update(
          "UPDATE command_outbox SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_attempt_at=UTC_TIMESTAMP()+INTERVAL 30 SECOND WHERE task_id=?",
          task.toString());
      call(DISPATCH, "scanAvailable");
      assertThat(count("dispatch_attempts")).isEqualTo(1);
      db.update(
          "UPDATE command_outbox SET next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      call(DISPATCH, "scanAvailable");
      assertThat(outbox(task).get("status")).isEqualTo("SENT");
      assertThat(outbox(task).get("event_id")).isEqualTo(reserved.get("event_id"));
      assertThat(count("dispatch_attempts")).isEqualTo(2);
    } finally {
      if (child.isAlive()) stopWorker(child);
    }
  }
}
