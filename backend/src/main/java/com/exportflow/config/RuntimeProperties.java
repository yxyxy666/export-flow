// 规格互链：[计划](../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 外部资源位置和有界运行配置；设计 decision-record.md，不记录凭据。 */
@ConfigurationProperties("export-flow")
public class RuntimeProperties {
  private Path fileRoot;
  private URI managementUrl = URI.create("http://127.0.0.1:15672");
  private boolean consumerEnabled;

  public Path getFileRoot() {
    return fileRoot;
  }

  public void setFileRoot(Path value) {
    fileRoot = value;
  }

  public URI getManagementUrl() {
    return managementUrl;
  }

  public void setManagementUrl(URI value) {
    managementUrl = value;
  }

  public boolean isConsumerEnabled() {
    return consumerEnabled;
  }

  public void setConsumerEnabled(boolean value) {
    consumerEnabled = value;
  }

  public Duration managementTimeout() {
    return Duration.ofSeconds(2);
  }

  public void validate() {
    if (fileRoot == null
        || !fileRoot.isAbsolute()
        || managementUrl == null
        || !java.util.Set.of("http", "https").contains(managementUrl.getScheme())
        || managementUrl.getHost() == null
        || managementUrl.getUserInfo() != null
        || !java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1")
            .contains(managementUrl.getHost())
        || managementUrl.getQuery() != null
        || managementUrl.getFragment() != null) throw new IllegalArgumentException("运行资源配置无效");
  }
}
