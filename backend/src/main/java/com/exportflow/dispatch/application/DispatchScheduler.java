// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[DispatchSchedulingTest.java](../../../../../../test/java/com/exportflow/dispatch/DispatchSchedulingTest.java)、[OutboxDeadlineRaceTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java)、[OutboxTimeoutMySqlTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java)。
package com.exportflow.dispatch.application;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 发布与到期裁决使用独立周期；设计 decision-record.md，TC-222。 */
@Component
public class DispatchScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(DispatchScheduler.class);
  private final OutboxDispatchService dispatch;
  private final OutboxTimeoutService timeout;
  private ScheduledExecutorService publishLoop, timeoutLoop;

  public DispatchScheduler(OutboxDispatchService dispatch, OutboxTimeoutService timeout) {
    this.dispatch = dispatch;
    this.timeout = timeout;
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (publishLoop != null) return;
    safeRun(() -> timeout.scanExpired());
    publishLoop =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("outbox-dispatch").factory());
    timeoutLoop =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("outbox-deadline").factory());
    publishLoop.scheduleWithFixedDelay(
        () -> safeRun(() -> dispatch.scanAvailable()), 1, 1, TimeUnit.SECONDS);
    timeoutLoop.scheduleWithFixedDelay(
        () -> safeRun(() -> timeout.scanExpired()), 5, 5, TimeUnit.SECONDS);
  }

  private void safeRun(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException unavailable) {
      LOG.warn("outbox dependency unavailable; persisted authority retained");
    }
  }

  public synchronized void stop(Duration grace) {
    if (publishLoop == null) return;
    publishLoop.shutdown();
    timeoutLoop.shutdown();
    long deadline = System.nanoTime() + grace.toNanos();
    try {
      publishLoop.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
      timeoutLoop.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } finally {
      publishLoop.shutdownNow();
      timeoutLoop.shutdownNow();
      publishLoop = null;
      timeoutLoop = null;
    }
  }

  @PreDestroy
  public void close() {
    stop(Duration.ofSeconds(15));
  }
}
