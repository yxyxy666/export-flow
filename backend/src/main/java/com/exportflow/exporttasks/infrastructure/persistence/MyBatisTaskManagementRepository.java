// 本次增量：[计划](../../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[暂停屏障](../../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.exporttasks.infrastructure.persistence;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.domain.TaskManagementModels.*;
import com.exportflow.exporttasks.port.*;
import com.exportflow.progress.port.ProgressRepository;
import com.exportflow.shared.domain.BusinessException;
import com.exportflow.worker.infrastructure.persistence.TaskExecutionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class MyBatisTaskManagementRepository implements TaskManagementRepository {
  private final TaskManagementMapper mapper;
  private final TaskManagementPersistenceMapper converter;
  private final ExportTaskRepository tasks;
  private final CommandOutboxMapper commands;
  private final TaskExecutionMapper executions;
  private final ProgressRepository progress;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;

  public MyBatisTaskManagementRepository(
      TaskManagementMapper mapper,
      TaskManagementPersistenceMapper converter,
      ExportTaskRepository tasks,
      CommandOutboxMapper commands,
      TaskExecutionMapper executions,
      ProgressRepository progress,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.converter = converter;
    this.tasks = tasks;
    this.commands = commands;
    this.executions = executions;
    this.progress = progress;
    this.json = json;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public TaskPage findPage(TaskQuery query) {
    var rows = mapper.findPage(query);
    var metadata = rows.getFirst();
    return new TaskPage(
        rows.stream().filter(row -> row.taskId() != null).map(converter::toItem).toList(),
        query.page(),
        query.pageSize(),
        metadata.total(),
        metadata.activityVersion(),
        metadata.hasActiveTasks());
  }

  public TaskListItem find(UUID id) {
    var row = mapper.find(id.toString());
    return row == null ? null : converter.toItem(row);
  }

  /** 活动锁在先，回执和业务状态原子提交；重放返回当前摘要。 */
  public PauseResult pause(PauseRequest request) {
    byte[] hash = hash(request.taskId(), request.expectedControlVersion());
    return transaction.execute(
        status -> {
          progress.lockActivity();
          var task = tasks.lockTask(request.taskId());
          var command = commands.lockByTask(request.taskId().toString());
          if (mapper.resumeOperation(request.operationId().toString()) != null)
            throw new BusinessException("OPERATION_ID_REUSED", "操作标识与请求不一致", 409);
          var existing = mapper.operation(request.operationId().toString());
          if (existing != null) {
            if (!existing.taskId().equals(request.taskId().toString())
                || !MessageDigest.isEqual(existing.requestHash(), hash))
              throw new BusinessException("OPERATION_ID_REUSED", "操作标识与请求不一致", 409);
            return new PauseResult("REPLAY", find(request.taskId()));
          }
          if (task == null) throw new BusinessException("TASK_NOT_FOUND", "任务不存在", 404);
          if (!Set.of(TaskStatus.PENDING, TaskStatus.RUNNING).contains(task.status()))
            throw new BusinessException("TASK_ACTION_NOT_ALLOWED", "任务当前状态不允许暂停", 409);
          if (task.controlVersion() != request.expectedControlVersion())
            throw new BusinessException("TASK_CONTROL_CONFLICT", "任务控制状态已改变", 409);
          executions.lock(request.taskId().toString());
          if (mapper.pause(request.taskId().toString(), request.expectedControlVersion()) != 1)
            throw new IllegalStateException("暂停裁决失效");
          if (task.status() == TaskStatus.PENDING && command != null)
            commands.close(command.eventId(), "USER_PAUSED", commands.databaseNow());
          var updated = find(request.taskId());
          try {
            mapper.insertOperation(
                request.operationId().toString(),
                request.taskId().toString(),
                hash,
                request.expectedControlVersion(),
                json.writeValueAsString(new PauseResult("APPLIED", updated)));
          } catch (java.io.IOException invalid) {
            throw new IllegalStateException("暂停回执编码失败");
          }
          progress.recordChange(request.taskId(), updated.version());
          return new PauseResult("APPLIED", updated);
        });
  }

  /** 原任务、原命令与回执同事务恢复；只接受已安全停止的PAUSED。 */
  public ResumeResult resume(ResumeRequest request) {
    byte[] hash = hash(request.taskId(), request.expectedControlVersion());
    return transaction.execute(
        status -> {
          progress.lockActivity();
          var task = tasks.lockTask(request.taskId());
          var command = commands.lockByTask(request.taskId().toString());
          if (mapper.operation(request.operationId().toString()) != null)
            throw new BusinessException("OPERATION_ID_REUSED", "操作标识与请求不一致", 409);
          var existing = mapper.resumeOperation(request.operationId().toString());
          if (existing != null) {
            if (!existing.taskId().equals(request.taskId().toString())
                || !MessageDigest.isEqual(existing.requestHash(), hash))
              throw new BusinessException("OPERATION_ID_REUSED", "操作标识与请求不一致", 409);
            return new ResumeResult("REPLAY", find(request.taskId()));
          }
          if (task == null) throw new BusinessException("TASK_NOT_FOUND", "任务不存在", 404);
          if (task.status() != TaskStatus.PAUSED)
            throw new BusinessException("TASK_ACTION_NOT_ALLOWED", "任务当前状态不允许继续", 409);
          if (task.controlVersion() != request.expectedControlVersion())
            throw new BusinessException("TASK_CONTROL_CONFLICT", "任务控制状态已改变", 409);
          executions.lock(request.taskId().toString());
          if (command == null || commands.reopen(request.taskId().toString()) != 1)
            throw new IllegalStateException("暂停命令封存尚未确认");
          if (mapper.resume(request.taskId().toString(), request.expectedControlVersion()) != 1)
            throw new IllegalStateException("继续裁决失效");
          var updated = find(request.taskId());
          try {
            mapper.insertResumeOperation(
                request.operationId().toString(),
                request.taskId().toString(),
                hash,
                request.expectedControlVersion(),
                json.writeValueAsString(new ResumeResult("APPLIED", updated)));
          } catch (java.io.IOException invalid) {
            throw new IllegalStateException("继续回执编码失败");
          }
          progress.recordChange(request.taskId(), updated.version());
          return new ResumeResult("APPLIED", updated);
        });
  }

  private byte[] hash(UUID taskId, long expectedControlVersion) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var value : List.of(taskId.toString(), Long.toString(expectedControlVersion))) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return digest.digest();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("摘要不可用");
    }
  }
}
