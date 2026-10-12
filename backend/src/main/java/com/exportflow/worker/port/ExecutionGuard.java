// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.port;

/** 受控停止检查；数据库写入还须核对权威身份。设计 function-design-reference.md。 */
@FunctionalInterface
public interface ExecutionGuard {
  boolean check();
}
