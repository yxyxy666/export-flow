// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BrokerPolicyBoundaryTest.java](../../../../../../../test/java/com/exportflow/dispatch/BrokerPolicyBoundaryTest.java)、[PublisherCorrelationTest.java](../../../../../../../test/java/com/exportflow/dispatch/PublisherCorrelationTest.java)、[RabbitMqPublisherTest.java](../../../../../../../test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java)。
package com.exportflow.dispatch.infrastructure.messaging;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.dispatch.port.ExportCommandPublisher;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 实际管理信息、mandatory/confirm/return共同证明；设计 contracts/export-command.md，TC-220。 */
@Component
public class RabbitMqExportCommandPublisher implements ExportCommandPublisher {
  private static final String EXCHANGE = "export-flow.commands",
      QUEUE = "export-flow.export.requested.v1",
      ROUTE = "export.requested.v1";
  private static final Logger LOG = LoggerFactory.getLogger(RabbitMqExportCommandPublisher.class);
  private final RabbitTemplate rabbit;
  private final ExportCommandCodec codec;
  private final RuntimeProperties properties;
  private final RabbitManagementClient management;
  private final String vhost;
  private long lastAlert;

  public RabbitMqExportCommandPublisher(
      RabbitTemplate rabbit,
      ExportCommandCodec codec,
      RuntimeProperties properties,
      RabbitManagementClient management) {
    this.rabbit = rabbit;
    this.codec = codec;
    this.properties = properties;
    rabbit.setMandatory(true);
    vhost = management.vhost();
    this.management = management;
  }

  public boolean verifyTopology() {
    return verifyTopology(System.nanoTime() + properties.managementTimeout().toNanos());
  }

  private boolean verifyTopology(long deadline) {
    try {
      String prefix = "/api/", encodedHost = encode(vhost);
      var queue = get(prefix + "queues/" + encodedHost + "/" + encode(QUEUE), deadline);
      var exchange = get(prefix + "exchanges/" + encodedHost + "/" + encode(EXCHANGE), deadline);
      var bindings =
          get(
              prefix + "exchanges/" + encodedHost + "/" + encode(EXCHANGE) + "/bindings/source",
              deadline);
      var policies = get(prefix + "policies/" + encodedHost, deadline);
      var operatorPolicies = get(prefix + "operator-policies/" + encodedHost, deadline);
      boolean valid =
          queue.path("durable").asBoolean()
              && !queue.path("auto_delete").asBoolean()
              && !queue.path("exclusive").asBoolean()
              && "classic".equals(queue.path("type").asText())
              && exchange.path("durable").asBoolean()
              && !exchange.path("auto_delete").asBoolean()
              && "direct".equals(exchange.path("type").asText());
      long matching = 0;
      for (var binding : bindings)
        if (ROUTE.equals(binding.path("routing_key").asText())) {
          matching++;
          valid &=
              QUEUE.equals(binding.path("destination").asText())
                  && "queue".equals(binding.path("destination_type").asText());
        }
      valid &=
          matching == 1
              && safeRetention(queue.path("arguments"))
              && safeRetention(queue.path("effective_policy_definition"));
      valid &= safeEffectivePolicies(policies) && safeEffectivePolicies(operatorPolicies);
      if (!valid) alert();
      return valid;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      alert();
      return false;
    } catch (Exception unavailable) {
      alert();
      return false;
    }
  }

  private boolean safeEffectivePolicies(JsonNode policies) {
    JsonNode selected = null;
    int priority = Integer.MIN_VALUE;
    for (var policy : policies) {
      String target = policy.path("apply-to").asText("all");
      if (!Set.of("all", "queues", "classic_queues").contains(target)) continue;
      if (java.util.regex.Pattern.compile(policy.path("pattern").asText()).matcher(QUEUE).find()
          && policy.path("priority").asInt() >= priority) {
        priority = policy.path("priority").asInt();
        selected = policy;
      }
    }
    return selected == null || safeRetention(selected.path("definition"));
  }

  private boolean safeRetention(JsonNode values) {
    for (String key :
        List.of(
            "message-ttl",
            "expires",
            "dead-letter-exchange",
            "dead-letter-routing-key",
            "delivery-limit")) if (values.has(key) || values.has("x-" + key)) return false;
    boolean limited =
        values.has("max-length")
            || values.has("max-length-bytes")
            || values.has("x-max-length")
            || values.has("x-max-length-bytes");
    String overflow = values.path("overflow").asText(values.path("x-overflow").asText());
    return (!limited || "reject-publish".equals(overflow))
        && (overflow.isEmpty() || "reject-publish".equals(overflow));
  }

  private JsonNode get(String path, long deadline) throws Exception {
    return management.get(path, deadline);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private synchronized void alert() {
    long now = System.nanoTime();
    if (lastAlert == 0 || now - lastAlert >= Duration.ofSeconds(30).toNanos()) {
      lastAlert = now;
      LOG.warn("queue topology or policy verification unavailable");
    }
  }

  public PublishResult publish(
      ExportCommandEnvelope envelope, DispatchLease lease, Duration budget) {
    long deadline = System.nanoTime() + budget.toNanos();
    var body = codec.encode(envelope);
    if (!verifyTopology(deadline))
      return new PublishResult(PublishKind.FAILED, null, "TOPOLOGY_UNVERIFIED");
    try {
      var props = new MessageProperties();
      props.setContentType("application/json");
      props.setContentEncoding("UTF-8");
      props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      props.setMessageId(envelope.eventId().toString());
      var correlation = new CorrelationData(lease.token().toString());
      rabbit.send(EXCHANGE, ROUTE, new Message(body, props), correlation);
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return new PublishResult(PublishKind.UNKNOWN, null, "PUBLISH_TIMEOUT");
      var confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
      if (!confirm.isAck() || correlation.getReturned() != null)
        return new PublishResult(PublishKind.FAILED, null, "PUBLISH_REJECTED");
      // return在Spring的相关confirm完成前已归并；再次核验运行中策略与唯一目标。
      if (!verifyTopology(deadline))
        return new PublishResult(PublishKind.UNKNOWN, null, "TOPOLOGY_UNVERIFIED");
      return new PublishResult(PublishKind.CONFIRMED, new PublishEvidence(true, true, true), null);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return new PublishResult(PublishKind.UNKNOWN, null, "PUBLISH_INTERRUPTED");
    } catch (org.springframework.amqp.AmqpConnectException unavailable) {
      return new PublishResult(PublishKind.UNKNOWN, null, "BROKER_UNAVAILABLE");
    } catch (Exception unknown) {
      return new PublishResult(PublishKind.UNKNOWN, null, "PUBLISH_UNKNOWN");
    }
  }
}
