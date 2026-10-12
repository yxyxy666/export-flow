// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.dispatch.infrastructure.persistence;

import java.time.LocalDateTime;

/** 独立数据库记录；设计 data-model.md。 */
public record CommandOutboxRecord(
    String eventId,
    String taskId,
    String type,
    int schemaVersion,
    String status,
    LocalDateTime acceptedAt,
    LocalDateTime dispatchDeadline,
    LocalDateTime nextAttemptAt,
    int attemptNo,
    String leaseOwner,
    String leaseToken,
    LocalDateTime leaseUntil) {}
