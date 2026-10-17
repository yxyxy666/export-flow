// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import java.time.LocalDateTime;

/** 独立SQL记录，不作为HTTP或领域模型复用。设计 data-model.md。 */
public record ExportTaskRecord(
    String taskId,
    String taskNo,
    String scopeType,
    String filterJson,
    String fieldsJson,
    long totalCount,
    long processedCount,
    String status,
    long version,
    LocalDateTime createdAt,
    LocalDateTime finishedAt,
    String errorCode,
    String errorSummary,
    String fileName,
    Long fileSize,
    String resultArtifactId,
    long controlVersion) {
  public record IdempotencyDbRecord(int canonicalVersion, byte[] requestHash, String taskId) {
    public IdempotencyDbRecord {
      requestHash = requestHash.clone();
    }

    @Override
    public byte[] requestHash() {
      return requestHash.clone();
    }
  }
}
