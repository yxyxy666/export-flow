// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import java.time.LocalDateTime;

/** 检查点数据库头及独立块记录；设计 data-model.md。 */
public record CheckpointRecord(
    String taskId,
    long dataGeneration,
    long checkpointVersion,
    long verifiedPrefixCount,
    LocalDateTime cursorCreatedAt,
    Long cursorOrderId,
    long processedHighWater,
    String configurationHash,
    int formatVersion) {
  public record BlockRecord(
      String blockId,
      String taskId,
      long dataGeneration,
      int sequence,
      String previousCursorJson,
      String lastCursorJson,
      int rowCount,
      long byteSize,
      String checksum,
      String storageRef,
      int formatVersion,
      String configurationHash,
      String createdByOwner,
      long createdByFence) {}
}
