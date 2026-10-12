// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.testing.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
class ExecutionSupervisionTest extends BoundaryFixture {
  private static final String SUPERVISOR = "com.exportflow.worker.application.ExecutionSupervisor";
  private static final String LISTENER =
      "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener";

  private boolean closed(com.rabbitmq.client.Channel channel) {
    return mockingDetails(channel).getInvocations().stream()
        .anyMatch(i -> java.util.Set.of("close", "abort").contains(i.getMethod().getName()));
  }

  @Test
  void TC224_claimTimeoutClosesOnlyStalledPendingDelivery() throws Exception {
    var task = create();
    var channel = mock(com.rabbitmq.client.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new BoundaryProbe.Gate()) {
      probe.on(
          EXECUTION,
          "claim",
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      var listener =
          threads.submit(
              () -> {
                try {
                  return call(LISTENER, "onMessage", command(task), channel, 71L);
                } catch (Exception controlled) {
                  return controlled;
                }
              });
      gate.await();
      assertThat(state(task)).isEqualTo("PENDING");
      org.awaitility.Awaitility.await()
          .during(Duration.ofSeconds(4))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () -> {
                call(SUPERVISOR, "checkClaims");
                assertThat(closed(channel)).isFalse();
                noAck(channel);
              });
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(9))
          .untilAsserted(
              () -> {
                call(SUPERVISOR, "checkClaims");
                assertThat(closed(channel)).isTrue();
              });
      noAck(channel);
      gate.close();
      listener.get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void TC224_claimMonitorDoesNotCloseHealthyRunningAfterFiveSeconds() throws Exception {
    var task = create();
    var channel = mock(com.rabbitmq.client.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new BoundaryProbe.Gate()) {
      probe.on(
          GENERATOR,
          "generate",
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      var listener = threads.submit(() -> call(LISTENER, "onMessage", command(task), channel, 72L));
      gate.await();
      assertThat(state(task)).isEqualTo("RUNNING");
      org.awaitility.Awaitility.await()
          .during(Duration.ofSeconds(6))
          .atMost(Duration.ofSeconds(9))
          .untilAsserted(
              () -> {
                call(SUPERVISOR, "checkClaims");
                assertThat(closed(channel)).isFalse();
              });
      gate.close();
      listener.get(20, TimeUnit.SECONDS);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"STALE", "UNKNOWN"})
  void TC224_heartbeatFailureStopsGuardBeforeAnyNewReadWriteOrTerminal(String outcome)
      throws Exception {
    var task = create();
    var lease = lease(task);
    var channel = mock(com.rabbitmq.client.Channel.class);
    var reached = new AtomicBoolean();
    probe.on(
        EXECUTION,
        "renew",
        (m, a, real) -> {
          reached.set(true);
          return enumValue(m.getReturnType(), outcome);
        });
    if (outcome.equals("UNKNOWN"))
      probe.on(
          EXECUTION,
          "find",
          (m, a, real) -> {
            throw new java.sql.SQLTransientConnectionException("synthetic unreadable");
          });
    assertThat(call(SUPERVISOR, "heartbeat", lease).toString()).isEqualTo(outcome);
    assertThat(reached).isTrue();
    // 监督器控制的真实guard由监听器注入生成器，在心跳故障后不得继续工作。
    probe.on(
        GENERATOR,
        "generate",
        (m, a, real) -> {
          call(SUPERVISOR, "heartbeat", property(a[0], "lease"));
          assertThat(property(a[1], "check")).isEqualTo(false);
          return unknown();
        });
    expireExecution(task);
    try {
      call(LISTENER, "onMessage", command(task), channel, 73L);
    } catch (java.lang.reflect.InvocationTargetException controlled) {
      /* 停止但不ACK。 */
    }
    noAck(channel);
    assertThat(count("export_artifacts")).isZero();
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC224_shutdownStopsGuardAndDoesNotReleaseBlockedExecutor() throws Exception {
    var task = create();
    var channel = mock(com.rabbitmq.client.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    var stopped = new AtomicBoolean();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
        var gate = new BoundaryProbe.Gate()) {
      probe.on(
          GENERATOR,
          "generate",
          (m, a, real) -> {
            Object lease = property(a[0], "lease");
            long fence = ((Number) property(lease, "fence")).longValue();
            gate.pause();
            stopped.set(!(boolean) property(a[1], "check"));
            assertThat(
                    db.queryForObject(
                        "SELECT fence FROM task_execution WHERE task_id=?",
                        Long.class,
                        task.toString()))
                .isEqualTo(fence);
            return unknown();
          });
      var listener = threads.submit(() -> call(LISTENER, "onMessage", command(task), channel, 74L));
      gate.await();
      long start = System.nanoTime();
      call(SUPERVISOR, "stop", Duration.ofSeconds(15));
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(17));
      assertThat(
              db.queryForObject(
                  "SELECT owner_id FROM task_execution WHERE task_id=?",
                  String.class,
                  task.toString()))
          .isNotBlank();
      noAck(channel);
      gate.close();
      listener.get(20, TimeUnit.SECONDS);
      assertThat(stopped).isTrue();
      assertThat(state(task)).isEqualTo("RUNNING");
    }
  }

  @Test
  void TC224_expiredAttemptCannotRenewEvenWithValidLease() throws Exception {
    var task = create();
    var lease = lease(task);
    db.update(
        "UPDATE task_execution SET attempt_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND,lease_until=UTC_TIMESTAMP()+INTERVAL 30 SECOND WHERE task_id=?",
        task.toString());
    assertThat(call(EXEC_SERVICE, "renew", lease).toString()).isEqualTo("STALE");
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  @org.junit.jupiter.api.Timeout(value = 7, unit = TimeUnit.MINUTES)
  void TC224_realFiveMinuteMonotonicAttemptBudgetStopsHealthyHeartbeatingWork() throws Exception {
    var task = create();
    var channel = mock(com.rabbitmq.client.Channel.class);
    when(channel.isOpen()).thenReturn(true);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var activeGuard = new AtomicReference<Object>();
    var activeLease = new AtomicReference<Object>();
    var heartbeatError = new AtomicReference<Throwable>();
    var started = new AtomicLong();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
        var heartbeats = Executors.newSingleThreadScheduledExecutor()) {
      probe.on(
          GENERATOR,
          "generate",
          (m, a, real) -> {
            activeGuard.set(a[1]);
            activeLease.set(property(a[0], "lease"));
            started.set(System.nanoTime());
            entered.countDown();
            if (!release.await(6, TimeUnit.MINUTES)) throw new AssertionError("测试工作线程未释放");
            return unknown();
          });
      var listener = threads.submit(() -> call(LISTENER, "onMessage", command(task), channel, 75L));
      try {
        assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
        heartbeats.scheduleAtFixedRate(
            () -> {
              try {
                call(SUPERVISOR, "heartbeat", activeLease.get());
              } catch (Throwable failure) {
                heartbeatError.set(failure);
              }
            },
            0,
            5,
            TimeUnit.SECONDS);
        // 实际单调时间边界；不通过修改DB时间冒充本地五分钟预算。
        org.awaitility.Awaitility.await()
            .pollInterval(Duration.ofSeconds(1))
            .during(Duration.ofMinutes(4).plusSeconds(58))
            .atMost(Duration.ofMinutes(5))
            .untilAsserted(
                () -> {
                  assertThat(heartbeatError.get()).isNull();
                  call(SUPERVISOR, "checkAttempts");
                  assertThat(property(activeGuard.get(), "check")).isEqualTo(true);
                  noAck(channel);
                });
        org.awaitility.Awaitility.await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(12))
            .untilAsserted(
                () -> {
                  call(SUPERVISOR, "checkAttempts");
                  assertThat(property(activeGuard.get(), "check")).isEqualTo(false);
                });
        assertThat(Duration.ofNanos(System.nanoTime() - started.get()))
            .isBetween(
                Duration.ofMinutes(5).minusSeconds(1), Duration.ofMinutes(5).plusSeconds(11));
        noAck(channel);
        assertThat(state(task)).isEqualTo("RUNNING");
      } finally {
        release.countDown();
        heartbeats.shutdownNow();
      }
      listener.get(20, TimeUnit.SECONDS);
      assertThat(count("export_artifacts")).isZero();
    }
  }
}
