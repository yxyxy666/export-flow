package com.exportflow.exporttasks.domain;

import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import java.util.UUID;

/** 统一删除用例的状态和结果；业务任务记录清理完成后不再保留任务根行。 */
public final class TaskDeletionModels {
  private TaskDeletionModels() {}

  public enum DeletionMode {
    ACTIVE_STOP,
    TERMINAL_CLEANUP
  }

  public enum DeletionState {
    NONE,
    REQUESTED,
    STOPPING,
    CLEANUP_PENDING,
    CLEANING,
    COMPLETED,
    FAILED_RETRYABLE
  }

  public record DeleteRequest(UUID taskId, UUID operationId, long expectedVersion) {
    public DeleteRequest {
      if (taskId == null || operationId == null || expectedVersion < 0)
        throw new IllegalArgumentException("删除请求无效");
    }
  }

  public record DeleteResult(
      String outcome,
      UUID taskId,
      UUID operationId,
      DeletionMode mode,
      DeletionState state,
      long version,
      TaskStatus priorStatus) {}
}
