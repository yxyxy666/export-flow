// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import com.exportflow.worker.domain.ExecutionModels.ExecutionLease;
import java.time.*;
import java.util.UUID;

/** 数据库记录显式UTC转换；设计 data-model.md。 */
public record ExecutionLeaseRecord(
    String taskId,
    String ownerId,
    long fence,
    LocalDateTime leaseUntil,
    LocalDateTime heartbeatAt,
    long progressVersion,
    LocalDateTime attemptStartedAt,
    LocalDateTime attemptDeadline,
    int recoveryCount,
    LocalDateTime nextRecoveryAt,
    long controlVersion) {
  public ExecutionLease toLease() {
    return new ExecutionLease(
        UUID.fromString(taskId),
        ownerId,
        fence,
        leaseUntil.toInstant(ZoneOffset.UTC),
        progressVersion,
        controlVersion);
  }

  public boolean accepts(ExecutionLease lease, Instant now) {
    return taskId.equals(lease.taskId().toString())
        && ownerId.equals(lease.owner())
        && fence == lease.fence()
        && controlVersion == lease.controlVersion()
        && now.isBefore(leaseUntil.toInstant(ZoneOffset.UTC))
        && now.isBefore(attemptDeadline.toInstant(ZoneOffset.UTC));
  }
}
