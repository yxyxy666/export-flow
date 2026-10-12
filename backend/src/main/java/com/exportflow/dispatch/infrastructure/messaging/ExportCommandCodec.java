// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BrokerPolicyBoundaryTest.java](../../../../../../../test/java/com/exportflow/dispatch/BrokerPolicyBoundaryTest.java)、[PublisherCorrelationTest.java](../../../../../../../test/java/com/exportflow/dispatch/PublisherCorrelationTest.java)、[RabbitMqPublisherTest.java](../../../../../../../test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java)。
package com.exportflow.dispatch.infrastructure.messaging;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;

/** 命令只携带服务端身份，严守版本与大小；设计 contracts/export-command.md，TC-220/223。 */
@Component
public class ExportCommandCodec {
  private final ObjectMapper json;

  public ExportCommandCodec(ObjectMapper mapper) {
    json =
        mapper
            .copy()
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    json.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  }

  public byte[] encode(ExportCommandEnvelope envelope) {
    validate(envelope);
    try {
      var body = json.writeValueAsBytes(envelope);
      if (body.length > 16384) throw new IllegalArgumentException("命令过大");
      return body;
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("命令编码失败");
    }
  }

  public ExportCommandEnvelope decode(byte[] body) {
    if (body == null || body.length > 16384) throw new IllegalArgumentException("命令大小无效");
    try {
      var envelope = json.readValue(body, ExportCommandEnvelope.class);
      validate(envelope);
      return envelope;
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("命令结构无效");
    }
  }

  private void validate(ExportCommandEnvelope value) {
    if (value == null
        || value.eventId() == null
        || value.taskId() == null
        || value.schemaVersion() != 1
        || !"EXPORT_REQUESTED".equals(value.type())
        || value.traceId() == null
        || !value.traceId().matches("[A-Za-z0-9_-]{1,128}"))
      throw new IllegalArgumentException("命令版本或身份无效");
  }
}
