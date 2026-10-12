// 规格与进度：[任务记录](../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationTest extends ProductionBackendFixture {
  @Test
  void TC213_localHealthIsAvailableWithoutExposingEnvironmentOrConfiguration() throws Exception {
    assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    assertThat(get("/actuator/health").body())
        .contains("UP")
        .doesNotContain(mysql.getJdbcUrl(), root.toString());
    assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
    assertThat(get("/actuator/configprops").statusCode()).isEqualTo(404);
  }

  @Test
  void TC213_realEntryUsesIsolatedServicesAndBoundedConsumerConfiguration() {
    assertThat(base.getHost()).isEqualTo("127.0.0.1");
    assertThat(app.getEnvironment().getProperty("spring.datasource.url"))
        .contains("export_flow_test");
    assertThat(app.getEnvironment().getProperty("export-flow.consumer-enabled")).isEqualTo("false");
    // 查询应用的真实Hikari连接；夹具用于观察的DriverManager连接有自己的会话配置。
    var runtime =
        new org.springframework.jdbc.core.JdbcTemplate(app.getBean(javax.sql.DataSource.class));
    assertThat(runtime.queryForObject("SELECT @@transaction_isolation", String.class))
        .isEqualTo("READ-COMMITTED");
    assertThat(runtime.queryForObject("SELECT @@session.time_zone", String.class))
        .isEqualTo("+00:00");
  }
}
