// 本次增量：[计划](../../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[权威摘要](../../../../../../../../../specs/004-task-management/data-model.md)。
package com.exportflow.exporttasks.infrastructure.persistence;

import com.exportflow.exporttasks.domain.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TaskManagementPersistenceMapper {
  private final ObjectMapper json;

  public TaskManagementPersistenceMapper(ObjectMapper json) {
    this.json = json;
  }

  /** 展示计数只读可信列；文件名与SQL搜索使用同一秒级香港时间。 */
  public TaskListItem toItem(TaskManagementMapper.ListRow row) {
    try {
      var status = TaskStatus.valueOf(row.status());
      List<ExportField> fields =
          json.readValue(row.fieldsJson(), new TypeReference<List<ExportField>>() {});
      ExportField.validateFields(fields);
      var actions = new java.util.ArrayList<String>();
      if (status == TaskStatus.SUCCEEDED) actions.add("DOWNLOAD");
      if (Set.of(TaskStatus.PENDING, TaskStatus.RUNNING).contains(status)) actions.add("PAUSE");
      if (status == TaskStatus.PAUSED) actions.add("RESUME");
      actions.add("DELETE");
      return new TaskListItem(
          UUID.fromString(row.taskId()),
          row.taskNo(),
          row.scopeType(),
          row.totalCount(),
          row.trustedProcessedCount(),
          status == TaskStatus.SUCCEEDED
              ? 100
              : (int) Math.min(99, row.trustedProcessedCount() * 100 / row.totalCount()),
          status,
          fields,
          row.fileName(),
          row.fileSize(),
          row.createdAt().toInstant(ZoneOffset.UTC),
          row.finishedAt() == null ? null : row.finishedAt().toInstant(ZoneOffset.UTC),
          row.errorCode(),
          row.errorSummary(),
          row.version(),
          row.controlVersion(),
          actions,
          row.executionPhase(),
          row.repairState(),
          row.repairEpoch(),
          row.repairAttempt(),
          row.repairReasonCode());
    } catch (java.io.IOException invalid) {
      throw new IllegalStateException("任务摘要配置无效");
    }
  }
}
