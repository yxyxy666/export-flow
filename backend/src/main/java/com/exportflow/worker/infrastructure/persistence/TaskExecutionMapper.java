// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.*;

/** task锁在先，execution锁在后；设计 function-design-reference.md，TC-224。 */
@Mapper
public interface TaskExecutionMapper {
  @Select(
      "SELECT t.status,t.control_version,e.owner_id AS owner,e.fence,e.lease_until,COALESCE(c.checkpoint_version,0) AS checkpoint_version,'KNOWN' AS read_outcome FROM export_tasks t LEFT JOIN task_execution e ON e.task_id=t.task_id LEFT JOIN export_checkpoints c ON c.task_id=t.task_id WHERE t.task_id=#{taskId}")
  com.exportflow.worker.domain.ExecutionModels.ControlSnapshot control(String taskId);

  @Update("UPDATE task_execution SET control_version=#{version} WHERE task_id=#{taskId}")
  int setControl(@Param("taskId") String taskId, @Param("version") long version);

  ExecutionLeaseRecord find(@Param("taskId") String taskId);

  ExecutionLeaseRecord lock(@Param("taskId") String taskId);

  int insertLease(
      @Param("taskId") String taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("until") Instant until,
      @Param("deadline") Instant deadline,
      @Param("recoveryAt") Instant recoveryAt);

  int takeOver(
      @Param("taskId") String taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("until") Instant until,
      @Param("deadline") Instant deadline,
      @Param("recoveryAt") Instant recoveryAt);

  int markRunning(@Param("taskId") String taskId);

  int renew(
      @Param("taskId") String taskId,
      @Param("now") Instant now,
      @Param("until") Instant until,
      @Param("recoveryAt") Instant recoveryAt);

  int releaseLease(@Param("taskId") String taskId, @Param("now") Instant now);

  int completeTask(
      @Param("taskId") String taskId,
      @Param("status") String status,
      @Param("now") Instant now,
      @Param("artifactId") String artifactId,
      @Param("fileName") String fileName,
      @Param("size") Long size,
      @Param("code") String code,
      @Param("message") String message);

  int advanceProgressVersion(@Param("taskId") String taskId);

  int updateProgress(@Param("taskId") String taskId, @Param("processed") long processed);
}
