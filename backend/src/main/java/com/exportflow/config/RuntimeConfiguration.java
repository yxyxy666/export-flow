// 规格互链：[计划](../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeConfigurationTest.java](../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)、[RuntimeMonitorUnitTest.java](../../../../../test/java/com/exportflow/RuntimeMonitorUnitTest.java)、[RuntimeSecurityTest.java](../../../../../test/java/com/exportflow/RuntimeSecurityTest.java)。
package com.exportflow.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

/** 只在配置的隔离vhost声明定稿拓扑；设计 decision-record.md，TC-213/220。 */
@Configuration
@EnableConfigurationProperties(RuntimeProperties.class)
public class RuntimeConfiguration {
  @Bean
  public Object validateRuntime(
      RuntimeProperties properties,
      org.springframework.core.env.Environment environment,
      org.springframework.amqp.rabbit.connection.CachingConnectionFactory factory) {
    properties.validate();
    String url = environment.getProperty("spring.datasource.url", "");
    try {
      if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
      var parsed = java.net.URI.create(url.substring(5));
      if (!java.util.Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(parsed.getHost())
          || !(java.util.Set.of("/export_flow", "/export_flow_test").contains(parsed.getPath())
              || parsed.getPath().matches("/export_flow_test_[0-9a-f]{32}"))
          || parsed.getUserInfo() != null
          || !("/export-flow".equals(factory.getRabbitConnectionFactory().getVirtualHost())
              || factory
                  .getRabbitConnectionFactory()
                  .getVirtualHost()
                  .matches("/export-flow-test-[0-9a-f]{32}"))) throw new IllegalArgumentException();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("运行隔离配置无效");
    }
    return new Object();
  }

  @Bean
  public Declarables exportTopology(RuntimeProperties properties) {
    properties.validate();
    var exchange = new DirectExchange("export-flow.commands", true, false);
    var queue =
        QueueBuilder.durable("export-flow.export.requested.v1")
            .withArgument("x-queue-type", "classic")
            .withArgument("x-consumer-timeout", 600000)
            .build();
    return new Declarables(
        exchange, queue, BindingBuilder.bind(queue).to(exchange).with("export.requested.v1"));
  }

  @Bean
  public RabbitAdmin exportAdmin(ConnectionFactory factory) {
    return new RabbitAdmin(factory);
  }

  @Bean
  public org.springframework.context.ApplicationListener<
          org.springframework.boot.context.event.ApplicationReadyEvent>
      initializeTopology(RabbitAdmin admin) {
    return event -> {
      try {
        admin.initialize();
      } catch (org.springframework.amqp.AmqpException unavailable) {
        org.slf4j.LoggerFactory.getLogger(RuntimeConfiguration.class)
            .warn("queue topology declaration unavailable");
      }
    };
  }
}
