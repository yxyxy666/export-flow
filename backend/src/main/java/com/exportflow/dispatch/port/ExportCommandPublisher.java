// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.dispatch.port;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import java.time.Duration;

/** 投递总预算内的确认和路由证明；设计 contracts/export-command.md。 */
public interface ExportCommandPublisher {
  boolean verifyTopology();

  PublishResult publish(ExportCommandEnvelope envelope, DispatchLease lease, Duration budget);
}
