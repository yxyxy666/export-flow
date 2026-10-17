// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[HTTP契约](../../../../../../../../specs/004-task-management/contracts/http-sse.md)。
package com.exportflow.exporttasks.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TaskManagementApiModels {
  private TaskManagementApiModels() {}

  public record PauseRequestDto(String operationId, String expectedControlVersion) {}

  public record TaskDto(
      UUID taskId,
      String taskNo,
      String scopeType,
      long totalCount,
      long processedCount,
      int progressPercent,
      String status,
      List<String> fields,
      String fileName,
      String fileSize,
      Instant createdAt,
      Instant finishedAt,
      String errorCode,
      String errorSummary,
      String version,
      String controlVersion,
      List<String> allowedActions,
      String executionPhase,
      String repairState,
      String repairEpoch,
      int repairAttempt,
      String repairReasonCode) {}

  public record TaskPageDto(
      List<TaskDto> items,
      int page,
      int pageSize,
      String total,
      String activityVersion,
      boolean hasActiveTasks) {}

  public record PauseResultDto(String outcome, TaskDto task) {}

  public record ResumeResultDto(String outcome, TaskDto task) {}
}
