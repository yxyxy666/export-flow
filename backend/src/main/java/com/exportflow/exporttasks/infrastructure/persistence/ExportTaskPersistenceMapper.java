// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 增量实施：[计划](../../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.*;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 领域/记录转换和配置回读；设计 function-design-reference.md，验证 TC-218/233。 */
@Component
public class ExportTaskPersistenceMapper {
  private final ObjectMapper json;

  public ExportTaskPersistenceMapper(ObjectMapper json) {
    this.json = json;
  }

  public ExportTaskRecord toRecord(ExportTask task, ExportScope scope, List<ExportField> fields) {
    try {
      return new ExportTaskRecord(
          task.taskId().toString(),
          task.taskNo(),
          task.scopeType(),
          scope.filter() == null ? null : json.writeValueAsString(scope.filter()),
          json.writeValueAsString(fields),
          task.totalCount(),
          task.processedCount(),
          task.status().name(),
          task.version(),
          LocalDateTime.ofInstant(task.createdAt(), ZoneOffset.UTC),
          task.finishedAt() == null
              ? null
              : LocalDateTime.ofInstant(task.finishedAt(), ZoneOffset.UTC),
          task.errorCode(),
          task.errorSummary(),
          task.fileName(),
          task.fileSize(),
          task.resultArtifactId() == null ? null : task.resultArtifactId().toString(),
          task.controlVersion());
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("配置持久化编码失败");
    }
  }

  public ExportTask toTask(ExportTaskRecord row) {
    return new ExportTask(
        UUID.fromString(row.taskId()),
        row.taskNo(),
        row.scopeType(),
        row.totalCount(),
        row.processedCount(),
        TaskStatus.valueOf(row.status()),
        row.version(),
        row.createdAt().toInstant(ZoneOffset.UTC),
        row.finishedAt() == null ? null : row.finishedAt().toInstant(ZoneOffset.UTC),
        row.errorCode(),
        row.errorSummary(),
        row.fileName(),
        row.fileSize(),
        row.resultArtifactId() == null ? null : UUID.fromString(row.resultArtifactId()),
        row.controlVersion());
  }

  private List<ExportField> fields(ExportTaskRecord row) {
    try {
      var result = json.readValue(row.fieldsJson(), new TypeReference<List<ExportField>>() {});
      ExportField.validateFields(result);
      return result;
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("持久配置无效");
    }
  }

  public TaskSummary toSummary(ExportTaskRecord row) {
    var task = toTask(row);
    int percent = (int) (task.processedCount() * 100 / task.totalCount());
    return new TaskSummary(
        task.taskId(),
        task.taskNo(),
        task.scopeType(),
        task.totalCount(),
        fields(row),
        task.status(),
        task.processedCount(),
        task.status() == TaskStatus.SUCCEEDED ? 100 : Math.min(99, percent),
        task.createdAt(),
        task.finishedAt(),
        task.errorCode(),
        task.errorSummary(),
        task.fileName(),
        task.fileSize());
  }

  public TaskSnapshot toSnapshot(ExportTaskRecord row, List<Long> orderIds) {
    try {
      var scope =
          "SELECTED".equals(row.scopeType())
              ? new ExportScope("SELECTED", orderIds, null)
              : new ExportScope(
                  "FILTERED", null, json.readValue(row.filterJson(), OrderFilter.class), orderIds);
      scope.validate();
      return new TaskSnapshot(toTask(row), scope, fields(row));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("持久范围无效");
    }
  }
}
