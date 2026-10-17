// 设计：[每实例可靠广播](../../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.infrastructure.messaging;

import com.exportflow.progress.application.TaskProgressService;
import com.exportflow.progress.domain.ProgressModels.ProgressNotice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 独立消费入口，发布端口的事务／故障代理不会隐藏监听方法。 */
@Component
public class RabbitProgressListener {
  private final TaskProgressService service;
  private final ObjectMapper json;

  public RabbitProgressListener(TaskProgressService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  @RabbitListener(
      id = "task-progress",
      queues = "#{progressQueue.name}",
      exclusive = true,
      concurrency = "1",
      ackMode = "MANUAL",
      autoStartup = "${export-flow.progress-enabled:true}")
  public void receive(Message message, Channel channel) throws java.io.IOException {
    boolean delivered = false;
    try {
      if (message.getBody().length <= 4096)
        delivered = service.onNotice(json.readValue(message.getBody(), ProgressNotice.class));
    } catch (RuntimeException | java.io.IOException unavailable) {
      /* 不把不可读消息确认或丢弃。 */
    }
    if (delivered) channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    else channel.abort(); // 保留未确认消息，由容器重新交付。
  }
}
