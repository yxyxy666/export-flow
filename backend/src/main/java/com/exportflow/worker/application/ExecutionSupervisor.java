// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.application;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.port.ExecutionGuard;
import com.exportflow.worker.port.ExportFileGenerator;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 监督周期独立于生成线程，失败停止guard且不释放活跃权利；设计 function-design-reference.md，TC-224。 */
@Component
public class ExecutionSupervisor {
  private static final Logger LOG = LoggerFactory.getLogger(ExecutionSupervisor.class);
  private final TaskExecutionService executions;
  private final RabbitListenerEndpointRegistry registry;
  private final RuntimeProperties properties;
  private final ExportFileGenerator generator;
  private final ConcurrentHashMap<String, ActiveDelivery> deliveries = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("execution-supervisor").factory());
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final AtomicBoolean stopped = new AtomicBoolean();

  public ExecutionSupervisor(
      TaskExecutionService executions,
      RabbitListenerEndpointRegistry registry,
      RuntimeProperties properties,
      ExportFileGenerator generator) {
    this.executions = executions;
    this.registry = registry;
    this.properties = properties;
    this.generator = generator;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    if (properties.isConsumerEnabled() && !generator.isAvailable())
      throw new IllegalStateException("文件生成能力不可用，不能开启消费");
    scheduler.scheduleWithFixedDelay(
        () -> {
          checkClaims();
          checkAttempts();
        },
        1,
        1,
        TimeUnit.SECONDS);
    if (properties.isConsumerEnabled()) registry.start();
  }

  public void onDelivery(DeliveryContext context, UUID taskId) {
    deliveries.put(context.channelId(), new ActiveDelivery(context, taskId));
  }

  public void attachChannel(String channelId, Runnable closer) {
    var item = deliveries.get(channelId);
    if (item != null) {
      boolean alreadyStopped;
      synchronized (item) {
        item.closer = closer;
        alreadyStopped = !item.valid.get();
      }
      if (alreadyStopped) closer.run();
    }
  }

  public ExecutionGuard onExecution(DeliveryContext context, ExecutionLease lease) {
    var item = deliveries.get(context.channelId());
    if (item == null) return () -> false;
    item.claimedAt = System.nanoTime();
    item.heartbeatAt = item.claimedAt;
    // 最后发布lease，监督线程看到执行权时计时字段已完整初始化。
    item.lease = lease;
    return new ExecutionGuard() {
      public boolean check() {
        return stopReason() == StopReason.NONE;
      }

      public StopReason stopReason() {
        if (!accepting.get() || !item.valid.get() || Thread.currentThread().isInterrupted())
          return StopReason.LOST_AUTHORITY;
        if (item.reason != StopReason.NONE) return item.reason;
        var control = executions.findControl(lease.taskId());
        if (control == null || !"KNOWN".equals(control.readOutcome())) return StopReason.UNKNOWN;
        if ("PAUSING".equals(control.status())
            && control.controlVersion() > lease.controlVersion()
            && lease.owner().equals(control.owner())
            && Objects.equals(lease.fence(), control.fence())) {
          item.reason = StopReason.USER_PAUSE;
          return item.reason;
        }
        return "RUNNING".equals(control.status())
                && control.controlVersion() == lease.controlVersion()
                && lease.owner().equals(control.owner())
                && Objects.equals(lease.fence(), control.fence())
            ? StopReason.NONE
            : StopReason.LOST_AUTHORITY;
      }
    };
  }

  public void finish(String channelId) {
    deliveries.remove(channelId);
  }

  public boolean isAccepting() {
    return accepting.get();
  }

  public LeaseWriteResult heartbeat(ExecutionLease lease) {
    var result = executions.renew(lease);
    if (result != LeaseWriteResult.APPLIED)
      for (var item : deliveries.values())
        if (item.lease != null
            && item.lease.taskId().equals(lease.taskId())
            && item.lease.fence() == lease.fence()
            && item.lease.owner().equals(lease.owner())) {
          var control = executions.findControl(lease.taskId());
          if (control != null
              && "PAUSING".equals(control.status())
              && lease.owner().equals(control.owner())
              && Objects.equals(control.fence(), lease.fence())
              && control.controlVersion() > lease.controlVersion())
            item.reason = StopReason.USER_PAUSE;
          else close(item);
        }
    return result;
  }

  public void checkClaims() {
    long now = System.nanoTime();
    for (var item : deliveries.values())
      if (item.lease == null && now - item.receivedAt >= Duration.ofSeconds(5).toNanos()) {
        var authority = executions.reconcile(item.taskId, "");
        if (authority.snapshot() == null
            || authority.snapshot().task().status()
                == com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus.PENDING)
          close(item);
      }
  }

  public void checkAttempts() {
    long now = System.nanoTime();
    for (var item : deliveries.values())
      if (item.lease != null && item.valid.get() && item.reason == StopReason.NONE) {
        if (now - item.claimedAt >= Duration.ofMinutes(5).toNanos()) {
          close(item);
          continue;
        }
        if (now - item.heartbeatAt >= Duration.ofSeconds(5).toNanos()) {
          item.heartbeatAt = now;
          heartbeat(item.lease);
        }
      }
  }

  private void close(ActiveDelivery item) {
    Runnable closer;
    synchronized (item) {
      if (!item.valid.compareAndSet(true, false)) return;
      closer = item.closer;
    }
    LOG.warn("worker delivery stopped; persisted authority retained");
    closer.run();
  }

  public void stop(Duration grace) {
    if (!stopped.compareAndSet(false, true)) return;
    long end = System.nanoTime() + grace.toNanos();
    accepting.set(false);
    deliveries.values().forEach(item -> item.valid.set(false));
    var containersStopped = new CountDownLatch(1);
    if (registry.isRunning()) registry.stop(containersStopped::countDown);
    else containersStopped.countDown();
    while ((!deliveries.isEmpty() || containersStopped.getCount() != 0)
        && System.nanoTime() < end) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    deliveries.values().forEach(item -> item.closer.run());
    scheduler.shutdownNow();
  }

  @PreDestroy
  @EventListener(org.springframework.context.event.ContextClosedEvent.class)
  public void close() {
    stop(Duration.ofSeconds(15));
  }

  private static final class ActiveDelivery {
    final DeliveryContext context;
    final UUID taskId;
    final long receivedAt = System.nanoTime();
    final AtomicBoolean valid = new AtomicBoolean(true);
    volatile ExecutionLease lease;
    volatile long claimedAt, heartbeatAt;
    volatile Runnable closer = () -> {};
    volatile StopReason reason = StopReason.NONE;

    ActiveDelivery(DeliveryContext context, UUID taskId) {
      this.context = context;
      this.taskId = taskId;
    }
  }
}
