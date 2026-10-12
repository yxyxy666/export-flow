// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
package com.exportflow.exporttasks.domain;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 持久任务权威状态；设计 data-model.md，验证 TC-218/222/224。 */
public record ExportTask(
    UUID taskId,
    String taskNo,
    String scopeType,
    long totalCount,
    long processedCount,
    TaskStatus status,
    long version,
    Instant createdAt,
    Instant finishedAt,
    String errorCode,
    String errorSummary,
    String fileName,
    Long fileSize,
    UUID resultArtifactId) {
  public static ExportTask pending(
      UUID id,
      String number,
      ExportScope scope,
      List<ExportField> fields,
      long total,
      Instant acceptedAt) {
    scope.validate();
    ExportField.validateFields(fields);
    if (total < 1 || total > 200000) throw new IllegalArgumentException("任务范围无效");
    return new ExportTask(
        id,
        number,
        scope.type(),
        total,
        0,
        TaskStatus.PENDING,
        0,
        acceptedAt,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public void validateProgress(long processed) {
    if (status != TaskStatus.RUNNING || processed < processedCount || processed > totalCount)
      throw new IllegalArgumentException("任务进度无效");
  }
}
