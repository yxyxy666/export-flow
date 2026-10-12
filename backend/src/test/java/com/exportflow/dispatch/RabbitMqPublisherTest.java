// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import com.rabbitmq.client.*;
import org.junit.jupiter.api.Test;

// [AC-056](../../../../../../../docs/product/acceptance-criteria.md#ac-056)
// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
class RabbitMqPublisherTest extends ProductionBackendFixture {
  protected Connection brokerConnection() throws Exception {
    var factory = new ConnectionFactory();
    factory.setUri(rabbit.getAmqpUrl());
    factory.setVirtualHost(rabbit.getVirtualHost());
    return factory.newConnection();
  }

  @Test
  void TC220_missingTargetBindingNeverMarksSentEvenIfExchangeAcceptsPublish() throws Exception {
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.queueUnbind(
          "export-flow.export.requested.v1", "export-flow.commands", "export.requested.v1");
      var task = create();
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
      assertThat(
              db.queryForObject(
                  "SELECT status FROM command_outbox WHERE task_id=?",
                  String.class,
                  task.toString()))
          .isNotEqualTo("SENT");
      assertThat(channel.basicGet("export-flow.export.requested.v1", true)).isNull();
      assertThat(count("command_outbox")).isEqualTo(1);
      assertThat(state(task)).isEqualTo("PENDING");
    }
  }

  @Test
  void TC220_rejectPublishCapacityKeepsOriginalOutboxRetryable() throws Exception {
    var policy =
        rabbit.execInContainer(
            "rabbitmqctl",
            "set_policy",
            "--vhost",
            rabbit.getVirtualHost(),
            "synthetic-capacity",
            "^export-flow\\.export\\.requested\\.v1$",
            "{\"max-length\":1,\"overflow\":\"reject-publish\"}",
            "--apply-to",
            "queues");
    assertThat(policy.getExitCode()).isZero();
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      channel.confirmSelect();
      channel.basicPublish(
          "export-flow.commands",
          "export.requested.v1",
          true,
          new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
          "synthetic-capacity-marker".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      channel.waitForConfirmsOrDie(5000);
      var task = create();
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
      assertThat(
              db.queryForObject(
                  "SELECT status FROM command_outbox WHERE task_id=?",
                  String.class,
                  task.toString()))
          .isNotEqualTo("SENT");
      assertThat(state(task)).isEqualTo("PENDING");
      assertThat(count("command_outbox")).isEqualTo(1);
      var marker = channel.basicGet("export-flow.export.requested.v1", false);
      assertThat(marker).isNotNull();
      assertThat(new String(marker.getBody(), java.nio.charset.StandardCharsets.UTF_8))
          .isEqualTo("synthetic-capacity-marker");
    }
  }

  @Test
  void TC220_durableConfirmedCommandSurvivesTestConnectionLoss() throws Exception {
    var task = create();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task.toString()))
        .isEqualTo("SENT");
    rabbit.suspendConnections();
    rabbit.resumeConnections();
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      var message = channel.basicGet("export-flow.export.requested.v1", false);
      assertThat(message).isNotNull();
      assertThat(json.readTree(message.getBody()).get("taskId").asText())
          .isEqualTo(task.toString());
      assertThat(message.getProps().getDeliveryMode()).isEqualTo(2);
      assertThat(state(task)).isEqualTo("PENDING");
    }
  }

  @Test
  void TC220_effectiveTtlPolicyBlocksSentMarkAndOriginalCommandIsRetained() throws Exception {
    var policy =
        rabbit.execInContainer(
            "rabbitmqctl",
            "set_policy",
            "--vhost",
            rabbit.getVirtualHost(),
            "synthetic-ttl",
            "^export-flow\\.export\\.requested\\.v1$",
            "{\"message-ttl\":1}",
            "--apply-to",
            "queues");
    assertThat(policy.getExitCode()).isZero();
    try {
      var task = create();
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
      assertThat(
              db.queryForObject(
                  "SELECT status FROM command_outbox WHERE task_id=?",
                  String.class,
                  task.toString()))
          .isNotEqualTo("SENT");
      assertThat(state(task)).isEqualTo("PENDING");
    } finally {
      assertThat(
              rabbit
                  .execInContainer(
                      "rabbitmqctl",
                      "clear_policy",
                      "--vhost",
                      rabbit.getVirtualHost(),
                      "synthetic-ttl")
                  .getExitCode())
          .isZero();
    }
  }

  @Test
  void TC220_sentRequiresRealConfirmedRoutedPersistentMessageAndKeepsItUnacknowledged()
      throws Exception {
    var task = create();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task.toString()))
        .isEqualTo("SENT");
    var factory = new ConnectionFactory();
    factory.setUri(rabbit.getAmqpUrl());
    factory.setVirtualHost(rabbit.getVirtualHost());
    try (var connection = factory.newConnection();
        var channel = connection.createChannel()) {
      var message = channel.basicGet("export-flow.export.requested.v1", false);
      assertThat(message).isNotNull();
      assertThat(message.getProps().getDeliveryMode()).isEqualTo(2);
      var command = json.readTree(message.getBody());
      assertThat(command.get("taskId").asText()).isEqualTo(task.toString());
      assertThat(command.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(state(task)).isEqualTo("PENDING");
      // 仅测试连接关闭；不ACK，原命令必须保留供真实Worker后续消费。
    }
  }
}
