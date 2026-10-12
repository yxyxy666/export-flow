// 规格互链：[计划](../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.config;

import org.apache.ibatis.annotations.*;

/** 只读运行计数；不加载客户输入、文件路径或命令载荷。 */
@Mapper
public interface RuntimeHealthMapper {
  @Select(
      "SELECT "
          + "(SELECT COUNT(*) FROM command_outbox o JOIN export_tasks t ON t.task_id=o.task_id WHERE t.status='PENDING' AND o.status NOT IN ('SENT','CLOSED') AND o.accepted_at<UTC_TIMESTAMP(3)-INTERVAL 60 SECOND) waiting_count,"
          + "(SELECT COUNT(*) FROM command_outbox o JOIN export_tasks t ON o.task_id=t.task_id WHERE o.status='SENT' AND t.status='PENDING' AND o.sent_at<UTC_TIMESTAMP(3)-INTERVAL 60 SECOND) sent_count,"
          + "(SELECT COUNT(*) FROM task_execution e JOIN export_tasks t ON e.task_id=t.task_id WHERE t.status='RUNNING' AND e.attempt_deadline<UTC_TIMESTAMP(3)) overdue_execution_count")
  Counts counts();

  record Counts(long waitingCount, long sentCount, long overdueExecutionCount) {}
}
