// 规格互链：[计划](../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.config;

import com.exportflow.dispatch.infrastructure.messaging.RabbitManagementClient;
import com.exportflow.worker.application.OrphanCleanupService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 五秒观察与十分钟清理独立循环；告警不改任务/消息权威事实。 */
@Component
public class RuntimeMonitor {
  private static final Logger LOG = LoggerFactory.getLogger(RuntimeMonitor.class);
  private final RuntimeHealthMapper database;
  private final RabbitManagementClient broker;
  private final OrphanCleanupService cleanup;
  private final Map<String, AlertState> alerts = new HashMap<>();
  private ScheduledExecutorService monitoring, cleaning;

  public RuntimeMonitor(
      RuntimeHealthMapper database, RabbitManagementClient broker, OrphanCleanupService cleanup) {
    this.database = database;
    this.broker = broker;
    this.cleanup = cleanup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (monitoring != null) return;
    monitoring =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("runtime-health").factory());
    cleaning =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("orphan-cleanup").factory());
    monitoring.scheduleWithFixedDelay(this::sample, 5, 5, TimeUnit.SECONDS);
    cleaning.scheduleWithFixedDelay(this::clean, 10, 10, TimeUnit.MINUTES);
  }

  public void sample() {
    RuntimeHealthMapper.Counts counts = null;
    try {
      counts = database.counts();
      alert("DATABASE_UNAVAILABLE", false, 0);
      alert("OUTBOX_WAITING", counts.waitingCount() > 0, counts.waitingCount());
      alert("SENT_PENDING", counts.sentCount() > 0, counts.sentCount());
    } catch (RuntimeException unavailable) {
      alert("DATABASE_UNAVAILABLE", true, 0);
    }
    try {
      var status = broker.snapshot();
      alert("BROKER_MONITOR_UNAVAILABLE", false, 0);
      alert("QUEUE_CAPACITY", status.messages() > 1000, status.messages());
      alert("NO_CONSUMER", status.messages() > 0 && status.consumers() == 0, status.messages());
      alert("BROKER_RESOURCE_ALARM", status.alarm(), 0);
      if (counts != null)
        alert(
            "UNACKED_ATTEMPT_OVERDUE",
            status.unacknowledged() > 0 && counts.overdueExecutionCount() > 0,
            status.unacknowledged());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception unavailable) {
      alert("BROKER_MONITOR_UNAVAILABLE", true, 0);
    }
  }

  private void clean() {
    try {
      cleanup.scanCandidates(20);
      alert("CLEANUP_UNAVAILABLE", false, 0);
    } catch (Exception unavailable) {
      alert("CLEANUP_UNAVAILABLE", true, 0);
    }
  }

  private synchronized void alert(String code, boolean active, long count) {
    long now = System.nanoTime();
    var prior = alerts.get(code);
    if (active
        && (prior == null
            || !prior.active()
            || now - prior.loggedAt() >= Duration.ofSeconds(30).toNanos())) {
      LOG.warn("runtime alert code={} count={}", code, count);
      alerts.put(code, new AlertState(true, now));
    } else if (!active && prior != null && prior.active()) {
      LOG.info("runtime recovered code={}", code);
      alerts.put(code, new AlertState(false, now));
    }
  }

  @jakarta.annotation.PreDestroy
  public synchronized void stop() {
    if (monitoring != null) monitoring.shutdownNow();
    if (cleaning != null) cleaning.shutdownNow();
  }

  private record AlertState(boolean active, long loggedAt) {}
}
