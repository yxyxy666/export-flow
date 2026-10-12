// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.BoundaryFixture;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
@ExtendWith(OutputCaptureExtension.class)
class BadCommandIsolationTest extends BoundaryFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @ParameterizedTest
  @ValueSource(strings = {"MISSING_FIELD", "UNKNOWN_TYPE", "TOO_LARGE", "TASK_ABSENT"})
  void TC223_realBadDeliveryStopsSlotAlertsAndRetainsOriginalUnackedCommand(
      String defect, CapturedOutput output) throws Exception {
    var task = create();
    var payload = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(command(task));
    if (defect.equals("MISSING_FIELD")) payload.remove("eventId");
    if (defect.equals("UNKNOWN_TYPE")) payload.put("type", "SYNTHETIC_UNKNOWN");
    if (defect.equals("TASK_ABSENT")) payload.put("taskId", UUID.randomUUID().toString());
    byte[] bytes =
        defect.equals("TOO_LARGE") ? new byte[16 * 1024 + 1] : json.writeValueAsBytes(payload);
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.confirmSelect();
      channel.basicPublish(
          "export-flow.commands",
          "export.requested.v1",
          true,
          com.rabbitmq.client.MessageProperties.PERSISTENT_BASIC,
          bytes);
      channel.waitForConfirmsOrDie(5000);
      var registry =
          app.getBean(
              org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class);
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> assertThat(registry.getListenerContainers()).allMatch(c -> !c.isRunning()));
      var retained = channel.basicGet("export-flow.export.requested.v1", false);
      assertThat(retained).isNotNull();
      assertThat(retained.getEnvelope().isRedeliver()).isTrue();
      assertThat(retained.getBody()).isEqualTo(bytes);
      assertThat(channel.basicGet("export-flow.export.requested.v1", false)).isNull();
      assertThat(output.toString()).containsPattern("(?is)(WARN|ERROR).*(command|message|消息|命令)");
      assertThat(output.toString())
          .doesNotContain(root.toString(), json.writeValueAsString(payload));
    }
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    assertThat(count("export_artifacts")).isZero();
  }
}
