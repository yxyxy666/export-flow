// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.port;

import com.exportflow.worker.domain.ExecutionModels.*;

/** 真实文件能力及安全生成分类；设计 contracts/worker-boundary.md。 */
public interface ExportFileGenerator {
  boolean isAvailable();

  GenerationResult generate(GenerationContext context, ExecutionGuard guard);
}
