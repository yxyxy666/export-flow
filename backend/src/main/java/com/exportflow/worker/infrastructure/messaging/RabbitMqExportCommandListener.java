// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeSecurityTest.java](../../../../../../../test/java/com/exportflow/RuntimeSecurityTest.java)、[BadCommandIsolationTest.java](../../../../../../../test/java/com/exportflow/worker/BadCommandIsolationTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)。
package com.exportflow.worker.infrastructure.messaging;

import com.exportflow.dispatch.infrastructure.messaging.ExportCommandCodec;
import com.exportflow.worker.application.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.rabbitmq.client.Channel;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 晚ACK，坏消息暂停消费槽；未知交付有界关闭归还原消息。设计 worker-boundary.md，TC-223/225。 */
@Component
public class RabbitMqExportCommandListener {
  private static final Logger LOG = LoggerFactory.getLogger(RabbitMqExportCommandListener.class);
  private final ExportCommandCodec codec;
  private final ExportCommandHandler handler;
  private final ExecutionSupervisor supervisor;
  private final org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry registry;
  private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
  private final ScheduledExecutorService delayed =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("delivery-recovery").factory());

  public RabbitMqExportCommandListener(
      ExportCommandCodec codec,
      ExportCommandHandler handler,
      ExecutionSupervisor supervisor,
      org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry registry) {
    this.codec = codec;
    this.handler = handler;
    this.supervisor = supervisor;
    this.registry = registry;
  }

  @RabbitListener(
      id = "exportWorker",
      queues = "export-flow.export.requested.v1",
      autoStartup = "false")
  public void onMessage(byte[] body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
      throws java.io.IOException {
    String id = UUID.randomUUID().toString();
    channels.put(id, channel);
    try {
      var command = codec.decode(body);
      var context = new DeliveryContext(id, tag, Instant.now());
      supervisor.onDelivery(context, command.taskId());
      supervisor.attachChannel(id, () -> closeDeliveryChannel(id));
      var decision = handler.handle(command, context);
      if (decision == DeliveryDecision.ACK) {
        channel.basicAck(tag, false);
        channels.remove(id);
      } else if (decision == DeliveryDecision.CLOSE_CHANNEL) {
        isolate(id);
      } else delayed.schedule(() -> closeDeliveryChannel(id), 30, TimeUnit.SECONDS);
    } catch (IllegalArgumentException malformed) {
      isolate(id);
    } finally {
      supervisor.finish(id);
    }
  }

  private void isolate(String id) {
    LOG.warn("worker command invalid; consumer slot stopped");
    var container = registry.getListenerContainer("exportWorker");
    if (container == null) {
      closeDeliveryChannel(id);
      return;
    }
    // 停止消费者后才归还坏消息，避免通道关闭触发消费者恢复并立即再次领取。
    delayed.execute(
        () -> {
          try {
            container.stop();
          } finally {
            closeDeliveryChannel(id);
          }
        });
  }

  public void closeDeliveryChannel(String id) {
    Channel channel = channels.remove(id);
    if (channel == null) return;
    try {
      channel.abort();
    } catch (Exception ignored) {
      LOG.warn("worker delivery channel close unconfirmed");
    }
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    channels.keySet().forEach(this::closeDeliveryChannel);
    delayed.shutdownNow();
  }
}
