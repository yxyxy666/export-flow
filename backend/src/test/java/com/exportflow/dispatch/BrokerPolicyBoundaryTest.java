// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// [AC-056](../../../../../../../docs/product/acceptance-criteria.md#ac-056)
@org.junit.jupiter.api.extension.ExtendWith(
    org.springframework.boot.test.system.OutputCaptureExtension.class)
class BrokerPolicyBoundaryTest extends ProductionBackendFixture {
  private org.springframework.boot.test.system.CapturedOutput captured;

  @org.junit.jupiter.api.BeforeEach
  void capture(org.springframework.boot.test.system.CapturedOutput output) {
    captured = output;
  }

  private void refuses() throws Exception {
    var task = create();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task.toString()))
        .isNotEqualTo("SENT");
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(captured.toString())
        .containsPattern("(?is)(WARN|ERROR).*(policy|queue|topology|策略|队列|拓扑)");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "policy|{\"expires\":1}",
        "policy|{\"max-length\":1,\"overflow\":\"drop-head\"}",
        "operator_policy|{\"message-ttl\":1}"
      })
  void TC220_effectiveQueueAndOperatorPoliciesAreRecheckedAtRuntime(String type, String payload)
      throws Exception {
    assertThat(
            rabbit
                .execInContainer(
                    "rabbitmqctl",
                    "set_" + type,
                    "--vhost",
                    rabbit.getVirtualHost(),
                    "synthetic-bad-retention",
                    "^export-flow\\.export\\.requested\\.v1$",
                    payload,
                    "--apply-to",
                    "queues")
                .getExitCode())
        .isZero();
    refuses();
  }

  @Test
  void TC220_extraBindingCannotProveUniqueCorrectTarget() throws Exception {
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.queueDeclare("synthetic-wrong-target", true, false, false, null);
      channel.queueBind("synthetic-wrong-target", "export-flow.commands", "export.requested.v1");
      refuses();
    }
  }

  @Test
  void TC220_managementUnreadableNeverPretendsPolicyIsVerified() throws Exception {
    String user =
        app.getBean(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.class)
            .getRabbitConnectionFactory()
            .getUsername();
    assertThat(rabbit.execInContainer("rabbitmqctl", "set_user_tags", user).getExitCode()).isZero();
    refuses();
  }

  @ParameterizedTest
  @CsvSource({"true,false,true", "true,true,false", "false,false,false"})
  void TC220_ephemeralQueueCannotBeMarkedReliable(
      boolean durable, boolean exclusive, boolean autoDelete) throws Exception {
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.queueDelete("export-flow.export.requested.v1");
      channel.queueDeclare("export-flow.export.requested.v1", durable, exclusive, autoDelete, null);
      channel.queueBind(
          "export-flow.export.requested.v1", "export-flow.commands", "export.requested.v1");
      refuses();
    }
  }

  @Test
  void TC220_capacityRecoveryRetriesSameEventAndOriginalMessagePropertiesAreExact()
      throws Exception {
    assertThat(
            rabbit
                .execInContainer(
                    "rabbitmqctl",
                    "set_policy",
                    "--vhost",
                    rabbit.getVirtualHost(),
                    "synthetic-capacity",
                    "^export-flow\\.export\\.requested\\.v1$",
                    "{\"max-length\":1,\"overflow\":\"reject-publish\"}",
                    "--apply-to",
                    "queues")
                .getExitCode())
        .isZero();
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.confirmSelect();
      channel.basicPublish(
          "export-flow.commands",
          "export.requested.v1",
          null,
          "synthetic capacity marker".getBytes());
      channel.waitForConfirmsOrDie(5000);
      var task = create();
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
      assertThat(
              db.queryForObject(
                  "SELECT status FROM command_outbox WHERE task_id=?",
                  String.class,
                  task.toString()))
          .isNotEqualTo("SENT");
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNotNull();
      db.update(
          "UPDATE command_outbox SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_attempt_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
      var message = channel.basicGet("export-flow.export.requested.v1", false);
      assertThat(message).isNotNull();
      var envelope = json.readTree(message.getBody());
      var names = new java.util.HashSet<String>();
      envelope.fieldNames().forEachRemaining(names::add);
      assertThat(names)
          .containsExactlyInAnyOrder("eventId", "taskId", "type", "schemaVersion", "traceId");
      String event =
          db.queryForObject(
              "SELECT event_id FROM command_outbox WHERE task_id=?", String.class, task.toString());
      assertThat(envelope.get("eventId").asText()).isEqualTo(event);
      assertThat(message.getProps().getMessageId()).isEqualTo(event);
      assertThat(message.getProps().getContentType()).isEqualTo("application/json");
      assertThat(message.getProps().getDeliveryMode()).isEqualTo(2);
      assertThat(envelope.get("taskId").asText()).isEqualTo(task.toString());
    }
  }
}
