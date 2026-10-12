// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// 规格：[消费边界](../../../../../../../specs/002-order-management/contracts/worker-boundary.md)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.dispatch.infrastructure.messaging.ExportCommandCodec;
import com.exportflow.worker.application.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener;
import com.exportflow.worker.port.ExportFileGenerator;
import com.rabbitmq.client.Channel;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.*;

class DeliveryIsolationUnitTest {
  @Test
  void TC223_invalidDeliveryStopsConsumerBeforeReturningMessageToBroker() throws Exception {
    var codec = mock(ExportCommandCodec.class);
    when(codec.decode(any())).thenThrow(new IllegalArgumentException("synthetic"));
    var registry = mock(RabbitListenerEndpointRegistry.class);
    var container = mock(MessageListenerContainer.class);
    when(registry.getListenerContainer("exportWorker")).thenReturn(container);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(container)
        .stop();
    var channel = mock(Channel.class);
    try (var listener =
        new ListenerResource(
            new RabbitMqExportCommandListener(
                codec,
                mock(ExportCommandHandler.class),
                mock(ExecutionSupervisor.class),
                registry))) {
      try {
        listener.value.onMessage(new byte[] {1}, channel, 1L);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        verify(channel, never()).abort();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
      } finally {
        release.countDown();
      }
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> verify(channel).abort());
    }
  }

  @Test
  void TC224_lateChannelAttachmentStillClosesDeliveryWhoseAuthorityWasLost() {
    var executions = mock(TaskExecutionService.class);
    when(executions.renew(any())).thenReturn(LeaseWriteResult.UNKNOWN);
    var supervisor =
        new ExecutionSupervisor(
            executions,
            mock(RabbitListenerEndpointRegistry.class),
            new RuntimeProperties(),
            mock(ExportFileGenerator.class));
    var id = UUID.randomUUID();
    var context = new DeliveryContext("synthetic", 1, Instant.now());
    var lease = new ExecutionLease(id, "owner", 1, Instant.now().plusSeconds(30), 1);
    var closed = new AtomicBoolean();
    try {
      supervisor.onDelivery(context, id);
      var guard = supervisor.onExecution(context, lease);
      supervisor.heartbeat(lease);
      assertThat(guard.check()).isFalse();
      supervisor.attachChannel(context.channelId(), () -> closed.set(true));
      assertThat(closed).isTrue();
    } finally {
      supervisor.finish(context.channelId());
      supervisor.stop(Duration.ZERO);
    }
  }

  private record ListenerResource(RabbitMqExportCommandListener value) implements AutoCloseable {
    public void close() {
      value.close();
    }
  }
}
