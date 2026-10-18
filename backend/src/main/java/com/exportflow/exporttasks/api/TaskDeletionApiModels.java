package com.exportflow.exporttasks.api;

import java.util.UUID;

/** 统一删除协议DTO；版本使用十进制字符串避免前端精度损失。 */
public final class TaskDeletionApiModels {
  private TaskDeletionApiModels() {}

  public record DeleteTaskRequestDto(UUID operationId, String expectedVersion) {}

  public record DeleteTaskResponseDto(
      String outcome,
      UUID taskId,
      UUID operationId,
      String mode,
      String state,
      String version,
      String priorStatus) {}
}
