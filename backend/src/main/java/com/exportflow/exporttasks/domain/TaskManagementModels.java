// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[任务模型](../../../../../../../../specs/004-task-management/data-model.md)。
package com.exportflow.exporttasks.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 权威摘要与控制请求；不携带执行身份、存储路径或订单原始数据。 */
public final class TaskManagementModels {
  private TaskManagementModels() {}

  public record TaskQuery(
      String keyword, ExportTaskModels.TaskStatus status, int page, int pageSize) {
    public TaskQuery {
      keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
      if (keyword != null && keyword.codePointCount(0, keyword.length()) > 100
          || page < 1
          || !List.of(20, 50, 100).contains(pageSize)) throw new IllegalArgumentException("查询无效");
    }

    public long offset() {
      return Math.multiplyExact((long) page - 1, pageSize);
    }

    public String pattern() {
      return keyword == null
          ? null
          : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
  }

  public record TaskListItem(
      UUID taskId,
      String taskNo,
      String scopeType,
      long totalCount,
      long processedCount,
      int progressPercent,
      ExportTaskModels.TaskStatus status,
      List<ExportField> fields,
      String fileName,
      Long fileSize,
      Instant createdAt,
      Instant finishedAt,
      String errorCode,
      String errorSummary,
      long version,
      long controlVersion,
      List<String> allowedActions,
      String executionPhase,
      String repairState,
      long repairEpoch,
      int repairAttempt,
      String repairReasonCode) {
    public TaskListItem {
      fields = List.copyOf(fields);
      allowedActions = List.copyOf(allowedActions);
    }
  }

  public record TaskPage(
      List<TaskListItem> items,
      int page,
      int pageSize,
      long total,
      long activityVersion,
      boolean hasActiveTasks) {
    public TaskPage {
      items = List.copyOf(items);
    }
  }

  public record PauseRequest(UUID taskId, UUID operationId, long expectedControlVersion) {
    public PauseRequest {
      if (taskId == null || operationId == null || expectedControlVersion < 0)
        throw new IllegalArgumentException("暂停请求无效");
    }
  }

  public record PauseResult(String outcome, TaskListItem task) {}

  /** 显式继续使用独立动作身份，不能自动解除暂停屏障。 */
  public record ResumeRequest(UUID taskId, UUID operationId, long expectedControlVersion) {
    public ResumeRequest {
      if (taskId == null || operationId == null || expectedControlVersion < 0)
        throw new IllegalArgumentException("继续请求无效");
    }
  }

  public record ResumeResult(String outcome, TaskListItem task) {}
}
