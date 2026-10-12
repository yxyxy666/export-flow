// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.dispatch.infrastructure.messaging;

import com.exportflow.config.RuntimeProperties;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.stereotype.Component;

/** 管理HTTP共用预算和凭据来源，不重定向、不输出凭据及响应原文。 */
@Component
public class RabbitManagementClient implements AutoCloseable {
  private final RuntimeProperties properties;
  private final ObjectMapper json;
  private final HttpClient client;
  private final String authorization, vhost;

  public RabbitManagementClient(
      CachingConnectionFactory factory, RuntimeProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
    var connection = factory.getRabbitConnectionFactory();
    vhost = connection.getVirtualHost();
    authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (connection.getUsername() + ":" + connection.getPassword())
                        .getBytes(StandardCharsets.UTF_8));
    client =
        HttpClient.newBuilder()
            .connectTimeout(properties.managementTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public String vhost() {
    return vhost;
  }

  public JsonNode get(String path, long deadline) throws Exception {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
    Duration timeout =
        Duration.ofNanos(Math.min(remaining, properties.managementTimeout().toNanos()));
    var response =
        client.send(
            HttpRequest.newBuilder(properties.getManagementUrl().resolve(path))
                .timeout(timeout)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) throw new java.io.IOException("管理核验不可用");
    return json.readTree(response.body());
  }

  public static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public BrokerSnapshot snapshot() throws Exception {
    long deadline = System.nanoTime() + properties.managementTimeout().toNanos();
    var queue = get("/api/queues/" + encode(vhost) + "/export-flow.export.requested.v1", deadline);
    var nodes = get("/api/nodes", deadline);
    boolean alarm = false;
    for (var node : nodes)
      alarm |= node.path("mem_alarm").asBoolean() || node.path("disk_free_alarm").asBoolean();
    if (!queue.has("messages") || !queue.has("messages_unacknowledged") || !queue.has("consumers"))
      throw new java.io.IOException("队列运行信息尚未就绪");
    return new BrokerSnapshot(
        queue.get("messages").asLong(),
        queue.get("messages_unacknowledged").asLong(),
        queue.get("consumers").asInt(),
        alarm);
  }

  public record BrokerSnapshot(long messages, long unacknowledged, int consumers, boolean alarm) {}

  @Override
  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }
}
