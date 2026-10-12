// [AC-061](../../../../../../docs/product/acceptance-criteria.md#ac-061)
// 设计与进度：[计划](../../../../../../specs/002-order-management/plan.md)、[任务](../../../../../../specs/002-order-management/tasks.md)。
package com.exportflow;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.mock.env.MockEnvironment;

class RuntimeIsolationUnitTest {
  @Test
  void TC213_acceptsOnlyStrictlyNamedSharedServiceTestResources() {
    var config = new RuntimeConfiguration();
    String suffix = "0123456789abcdef0123456789abcdef";
    var properties = new RuntimeProperties();
    properties.setFileRoot(
        java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath());
    var factory = new CachingConnectionFactory();
    try {
      factory.setVirtualHost("/export-flow-test-" + suffix);
      var env =
          new MockEnvironment()
              .withProperty(
                  "spring.datasource.url", "jdbc:mysql://127.0.0.1/export_flow_test_" + suffix);
      assertThatCode(() -> config.validateRuntime(properties, env, factory))
          .doesNotThrowAnyException();
      for (String path :
          new String[] {
            "example", "export_flow_test_bad", "export_flow_test_" + suffix + "extra"
          }) {
        env.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1/" + path);
        assertThatThrownBy(() -> config.validateRuntime(properties, env, factory))
            .isInstanceOf(IllegalArgumentException.class);
      }
      env.setProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1/export_flow_test_" + suffix);
      factory.setVirtualHost("/");
      assertThatThrownBy(() -> config.validateRuntime(properties, env, factory))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      factory.destroy();
    }
  }
}
