// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.*;

/** task锁在先，execution锁在后；设计 function-design-reference.md，TC-224。 */
@Mapper
public interface TaskExecutionMapper {
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
