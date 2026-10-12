// 规格互链：[计划](../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeConfigurationTest.java](../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)、[RuntimeMonitorUnitTest.java](../../../../test/java/com/exportflow/RuntimeMonitorUnitTest.java)、[RuntimeSecurityTest.java](../../../../test/java/com/exportflow/RuntimeSecurityTest.java)。
package com.exportflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 正式入口；设计 specs/002-order-management/plan.md，验证 TC-213。 */
@SpringBootApplication
public class ExportFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExportFlowApplication.class, args);
  }
}
