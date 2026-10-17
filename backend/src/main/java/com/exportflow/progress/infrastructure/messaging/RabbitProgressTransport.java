// 设计：[每实例可靠广播](../../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.infrastructure.messaging;

import com.exportflow.progress.domain.ProgressModels.*;
import com.exportflow.progress.port.ProgressPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitProgressTransport implements ProgressPublisher {
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public RabbitProgressTransport(RabbitTemplate rabbit, ObjectMapper json) {
    this.rabbit = rabbit;
    this.json = json;
    rabbit.setMandatory(true);
  }

  public PublishProof publish(ProgressNotice notice) {
    try {
      var properties = new MessageProperties();
      properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      properties.setContentType("application/json");
      var correlation = new CorrelationData(java.util.UUID.randomUUID().toString());
      rabbit.send(
          "exportflow.progress.v1",
          "",
          new Message(json.writeValueAsBytes(notice), properties),
          correlation);
      var confirmed = correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
      return new PublishProof(confirmed.isAck(), correlation.getReturned() == null);
    } catch (Exception unavailable) {
      if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
      return new PublishProof(false, false);
    }
  }
}
