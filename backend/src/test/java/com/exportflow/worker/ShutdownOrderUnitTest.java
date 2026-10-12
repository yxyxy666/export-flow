// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// 设计：[停止边界](../../../../../../../specs/002-order-management/contracts/worker-boundary.md)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.worker.application.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.port.ExportFileGenerator;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ShutdownOrderUnitTest {
  @Test
  void TC224_inactiveConsumerShutdownDoesNotSpendGraceWaitingForUnusedCallback() {
    var supervisor =
        new ExecutionSupervisor(
            mock(TaskExecutionService.class),
            mock(RabbitListenerEndpointRegistry.class),
            new RuntimeProperties(),
            mock(ExportFileGenerator.class));
    org.junit.jupiter.api.Assertions.assertTimeout(
        Duration.ofSeconds(1), () -> supervisor.stop(Duration.ofSeconds(3)));
  }

  @Test
  void TC224_contextCloseInvalidatesGuardBeforeLifecycleReturnsOriginalDelivery() throws Exception {
    var registry = mock(RabbitListenerEndpointRegistry.class);
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(registry)
        .stop(any(Runnable.class));
    var supervisor =
        new ExecutionSupervisor(
            mock(TaskExecutionService.class),
            registry,
            new RuntimeProperties(),
            mock(ExportFileGenerator.class));
    var id = UUID.randomUUID();
    var delivery = new DeliveryContext("synthetic-close", 1, Instant.now());
    supervisor.onDelivery(delivery, id);
    var guard =
        supervisor.onExecution(
            delivery, new ExecutionLease(id, "owner", 1, Instant.now().plusSeconds(30), 0));
    var runningAtChannelStop = new AtomicBoolean(true);
    try (var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newSingleThreadExecutor()) {
      context.registerBean(ExecutionSupervisor.class, () -> supervisor);
      // 模拟Spring管理的通道生命周期；其停止发生在bean销毁之前。
      context.registerBean(
          "deliveryLifecycle",
          SmartLifecycle.class,
          () ->
              new SmartLifecycle() {
                private boolean running;

                public void start() {
                  running = true;
                }

                public void stop() {
                  runningAtChannelStop.set(guard.check());
                  running = false;
                }

                public boolean isRunning() {
                  return running;
                }
              });
      context.refresh();
      var work =
          executor.submit(
              () -> {
                org.awaitility.Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> !guard.check());
                supervisor.finish(delivery.channelId());
              });
      context.close();
      work.get(5, TimeUnit.SECONDS);
      assertThat(runningAtChannelStop).isFalse();
    }
  }
}
