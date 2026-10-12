// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import java.time.LocalDateTime;

/** 不可变文件证明的数据库记录；设计 data-model.md。 */
public record ArtifactRecord(
    String artifactId,
    String taskId,
    long dataGeneration,
    String configurationHash,
    String storageRef,
    long rowCount,
    long byteSize,
    String checksum,
    String fileName,
    String state,
    String createdByOwner,
    long createdByFence,
    LocalDateTime readyAt,
    Long adoptedByFence,
    String cleanupToken) {}
