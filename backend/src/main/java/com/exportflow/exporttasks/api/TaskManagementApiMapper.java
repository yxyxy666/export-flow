// 设计：[HTTP契约](../../../../../../../../specs/004-task-management/contracts/http-sse.md)。
package com.exportflow.exporttasks.api;

import com.exportflow.exporttasks.api.TaskManagementApiModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.domain.TaskManagementModels.*;
import com.exportflow.shared.domain.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
public class TaskManagementApiMapper {
  /** 先拒绝重复／未知字段，再解析数字，禁止隐式截断和溢出。 */
  public TaskQuery toQuery(MultiValueMap<String, String> input) {
    if (input.entrySet().stream()
        .anyMatch(
            entry ->
                !Set.of("keyword", "status", "page", "pageSize").contains(entry.getKey())
                    || entry.getValue().size() != 1)) throw BusinessException.validation();
    String status = input.getFirst("status");
    return new TaskQuery(
        input.getFirst("keyword"),
        status == null ? null : TaskStatus.valueOf(status),
        integer(input.getFirst("page"), 1),
        integer(input.getFirst("pageSize"), 20));
  }

  private int integer(String value, int fallback) {
    if (value == null) return fallback;
    if (!value.matches("[1-9][0-9]*")) throw BusinessException.validation();
    return Integer.parseInt(value);
  }

  public PauseRequest toPause(UUID taskId, PauseRequestDto dto) {
    if (dto == null
        || dto.operationId() == null
        || !dto.operationId().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        || dto.expectedControlVersion() == null
        || !dto.expectedControlVersion().matches("0|[1-9][0-9]*"))
      throw BusinessException.validation();
    return new PauseRequest(
        taskId, UUID.fromString(dto.operationId()), Long.parseLong(dto.expectedControlVersion()));
  }

  public TaskDto toDto(TaskListItem task) {
    return new TaskDto(
        task.taskId(),
        task.taskNo(),
        task.scopeType(),
        task.totalCount(),
        task.processedCount(),
        task.progressPercent(),
        task.status().name(),
        task.fields().stream().map(Enum::name).toList(),
        task.fileName(),
        task.fileSize() == null ? null : task.fileSize().toString(),
        task.createdAt(),
        task.finishedAt(),
        task.errorCode(),
        task.errorSummary(),
        Long.toString(task.version()),
        Long.toString(task.controlVersion()),
        task.allowedActions(),
        task.executionPhase(),
        task.repairState(),
        Long.toString(task.repairEpoch()),
        task.repairAttempt(),
        task.repairReasonCode());
  }

  public TaskPageDto toDto(TaskPage page) {
    return new TaskPageDto(
        page.items().stream().map(this::toDto).toList(),
        page.page(),
        page.pageSize(),
        Long.toString(page.total()),
        Long.toString(page.activityVersion()),
        page.hasActiveTasks());
  }
}
