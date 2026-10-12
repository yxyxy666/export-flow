// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.port;

import static com.exportflow.worker.domain.ExecutionModels.*;

import java.util.UUID;

/** 数据库权威执行权，事务外不授予权限；设计 function-design-reference.md。 */
public interface TaskExecutionRepository {
  ClaimResult claim(UUID taskId, String owner, ExecutionPolicy policy);

  LeaseWriteResult renew(ExecutionLease lease);

  LeaseWriteResult complete(ExecutionLease lease, GenerationResult result);

  ClaimResult find(UUID taskId);
}
