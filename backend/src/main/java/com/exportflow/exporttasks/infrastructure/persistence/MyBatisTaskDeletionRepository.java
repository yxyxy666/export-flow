package com.exportflow.exporttasks.infrastructure.persistence;

import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.domain.TaskDeletionModels.*;
import com.exportflow.exporttasks.port.TaskDeletionRepository;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.progress.port.ProgressRepository;
import com.exportflow.shared.domain.BusinessException;
import com.exportflow.worker.infrastructure.persistence.TaskExecutionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 任务业务记录硬删除；独立技术表保留迟到消息裁决身份。 */
@Repository
public class MyBatisTaskDeletionRepository implements TaskDeletionRepository {
  private final TaskDeletionMapper mapper;
  private final TaskExecutionMapper executions;
  private final ProgressRepository progress;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;
  private final ExportFileStorage storage;

  public MyBatisTaskDeletionRepository(
      TaskDeletionMapper mapper,
      TaskExecutionMapper executions,
      ProgressRepository progress,
      ObjectMapper json,
      PlatformTransactionManager manager,
      ExportFileStorage storage) {
    this.mapper = mapper;
    this.executions = executions;
    this.progress = progress;
    this.json = json;
    this.transaction = new TransactionTemplate(manager);
    this.transaction.setTimeout(5);
    this.storage = storage;
  }

  @Override
  public DeleteResult delete(DeleteRequest request) {
    byte[] hash = hash(request);
    return transaction.execute(
        ignored -> {
          var existing = mapper.lockOperation(request.operationId().toString());
          if (existing != null) {
            if (!existing.taskId().equals(request.taskId().toString())
                || !MessageDigest.isEqual(existing.requestHash(), hash))
              throw new BusinessException("OPERATION_ID_REUSED", "操作标识与请求不一致", 409);
            return replay(existing);
          }

          progress.lockActivity();
          var task = mapper.lockTask(request.taskId().toString());
          if (task == null) throw new BusinessException("TASK_NOT_FOUND", "任务不存在", 404);
          if (!"NONE".equals(task.deletionState()))
            throw new BusinessException("TASK_DELETE_NOT_ALLOWED", "任务正在删除", 409);
          if (task.version() != request.expectedVersion())
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务状态已改变，请刷新后重试", 409);

          TaskStatus prior;
          try {
            prior = TaskStatus.valueOf(task.status());
          } catch (IllegalArgumentException invalid) {
            throw new BusinessException("TASK_DELETE_NOT_ALLOWED", "任务状态不支持删除", 409);
          }
          DeletionMode mode =
              Set.of(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.PAUSING, TaskStatus.PAUSED)
                      .contains(prior)
                  ? DeletionMode.ACTIVE_STOP
                  : DeletionMode.TERMINAL_CLEANUP;
          String state = mode == DeletionMode.ACTIVE_STOP ? "STOPPING" : "CLEANUP_PENDING";
          if (mapper.requestDelete(
              request.taskId().toString(),
              request.expectedVersion(),
              request.operationId().toString(),
              mode.name(),
              state) != 1)
            throw new BusinessException("TASK_VERSION_CONFLICT", "任务状态已改变，请刷新后重试", 409);
          String jobId = UUID.randomUUID().toString();
          mapper.closeCommands(request.taskId().toString());
          var newVersion = task.version() + 1;
          var liveLease = executions.lock(request.taskId().toString());
          boolean alreadyStopped = liveLease == null || !Instant.now().isBefore(
              liveLease.leaseUntil().toInstant(java.time.ZoneOffset.UTC));
          var acceptedState = mode == DeletionMode.ACTIVE_STOP && !alreadyStopped
              ? DeletionState.STOPPING : DeletionState.COMPLETED;
          var accepted =
              new DeleteResult(
                  "ACCEPTED", request.taskId(), request.operationId(), mode, acceptedState, newVersion, prior);
          mapper.insertOperation(
              request.operationId().toString(), request.taskId().toString(), hash,
              request.expectedVersion(), mode.name(), prior.name(),
              acceptedState == DeletionState.STOPPING ? "STOPPING" : "COMPLETED", encode(accepted));
          mapper.insertJob(
              jobId,
              request.taskId().toString(),
              request.operationId().toString(),
               acceptedState == DeletionState.STOPPING ? "STOP_WAIT" : "DB_CLEANUP");

          if (mode == DeletionMode.ACTIVE_STOP) {
            executions.stopForDeletion(request.taskId().toString(), task.controlVersion() + 1);
            if (alreadyStopped) {
              hardDeleteBusinessRows(request.taskId().toString());
              mapper.upsertTombstone(request.taskId().toString(), hex(hash(request.taskId().toString())), request.operationId().toString());
              mapper.updateJob(jobId, "FILE_CLEANUP", null);
            }
          } else {
            hardDeleteBusinessRows(request.taskId().toString());
            mapper.upsertTombstone(
                request.taskId().toString(), hex(hash(request.taskId().toString())), request.operationId().toString());
            mapper.updateJob(jobId, "FILE_CLEANUP", null);
          }
          mapper.incrementActivity();
          return accepted;
        });
  }

  /** 每秒领取一条删除作业；数据库事务和文件IO严格分离。 */
  @Scheduled(fixedDelay = 1000)
  public void processDeletionJobs() {
    try {
      for (int i = 0; i < 20; i++) {
        if (!processOneJob()) return;
      }
    } catch (RuntimeException ignored) {
      // 依赖不可用时保留作业，下一轮按租约和退避继续处理。
    }
  }

  private boolean processOneJob() {
    String owner = "deletion-" + UUID.randomUUID();
    var job = transaction.execute(ignored -> {
      var due = mapper.lockDueJob();
      if (due == null || mapper.claimJob(due.jobId(), owner) != 1) return null;
      return due;
    });
    if (job == null) return false;
    try {
      if ("FILE_CLEANUP".equals(job.phase())) {
        try {
          storage.deleteTaskFiles(UUID.fromString(job.taskId()));
          transaction.execute(ignored -> { mapper.completeJob(job.jobId(), owner); return null; });
        } catch (java.io.IOException failure) {
          long delay = Math.min(300, 1L << Math.min(job.attemptNo(), 8));
          transaction.execute(ignored -> { mapper.retryJob(job.jobId(), "RETRYABLE_FAILURE", "FILE_CLEANUP_UNAVAILABLE", delay, owner); return null; });
        }
        return true;
      }
      boolean deleted = transaction.execute(ignored -> {
        var task = mapper.lockTask(job.taskId());
        if (task == null) {
          mapper.updateJob(job.jobId(), "FILE_CLEANUP", null);
          return true;
        }
        var lease = executions.lock(job.taskId());
        if (lease != null && lease.leaseUntil() != null
            && Instant.now().isBefore(lease.leaseUntil().toInstant(java.time.ZoneOffset.UTC))) return false;
        hardDeleteBusinessRows(job.taskId());
        mapper.upsertTombstone(job.taskId(), hex(hash(job.taskId())), job.operationId());
        mapper.incrementActivity();
        mapper.updateJob(job.jobId(), "FILE_CLEANUP", null);
        var op = mapper.findOperation(job.operationId());
        if (op != null) {
          var complete = new DeleteResult("COMPLETED", UUID.fromString(job.taskId()), UUID.fromString(job.operationId()),
              DeletionMode.valueOf(op.mode()), DeletionState.COMPLETED, task.version(), TaskStatus.valueOf(op.priorStatus()));
          mapper.updateOperation(job.operationId(), "COMPLETED", encode(complete));
        }
        return true;
      });
      if (!deleted) {
        transaction.execute(ignored -> { mapper.retryJob(job.jobId(), "STOP_WAIT", "WORKER_STOPPING", 1, owner); return null; });
        return true;
      }
      try {
        storage.deleteTaskFiles(UUID.fromString(job.taskId()));
        transaction.execute(ignored -> { mapper.completeJob(job.jobId(), owner); return null; });
      } catch (java.io.IOException failure) {
        long delay = Math.min(300, 1L << Math.min(job.attemptNo(), 8));
        transaction.execute(ignored -> { mapper.retryJob(job.jobId(), "RETRYABLE_FAILURE", "FILE_CLEANUP_UNAVAILABLE", delay, owner); return null; });
      }
      return true;
    } catch (RuntimeException failure) {
      transaction.execute(ignored -> { mapper.retryJob(job.jobId(), "RETRYABLE_FAILURE", "DELETE_DEPENDENCY_UNAVAILABLE", 5, owner); return null; });
      return true;
    }
  }

  private String encode(DeleteResult result) {
    try {
      return json.writeValueAsString(result);
    } catch (java.io.IOException invalid) {
      throw new IllegalStateException("删除回执编码失败");
    }
  }

  private void hardDeleteBusinessRows(String taskId) {
    for (String event : mapper.commandIds(taskId)) mapper.deleteDispatchAttempts(event);
    mapper.deletePauseOperations(taskId);
    mapper.deleteResumeOperations(taskId);
    mapper.deleteProgressOutbox(taskId);
    mapper.deleteRepairSegments(taskId);
    mapper.deleteRepairs(taskId);
    mapper.deleteDataSegments(taskId);
    mapper.deleteDataBlocks(taskId);
    mapper.deleteCheckpoints(taskId);
    mapper.deleteCleanupCandidates(taskId);
    mapper.detachResultArtifact(taskId);
    mapper.deleteArtifacts(taskId);
    mapper.deleteTaskOrders(taskId);
    mapper.deleteIdempotency(taskId);
    mapper.deleteExecution(taskId);
    mapper.deleteCommands(taskId);
    if (mapper.deleteTask(taskId) != 1) throw new IllegalStateException("任务删除裁决失效");
  }

  private DeleteResult replay(TaskDeletionMapper.OperationRow row) {
    try {
      var saved = json.readValue(row.resultJson(), DeleteResult.class);
      return new DeleteResult("REPLAY", saved.taskId(), saved.operationId(), saved.mode(), saved.state(), saved.version(), saved.priorStatus());
    } catch (java.io.IOException invalid) {
      throw new IllegalStateException("删除回执不可恢复");
    }
  }

  private byte[] hash(DeleteRequest request) {
    return hash(request.taskId().toString() + "\u0000" + request.expectedVersion());
  }

  private byte[] hash(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(ByteBuffer.allocate(4).putInt(value.length()).array());
      digest.update(value.getBytes(StandardCharsets.UTF_8));
      return digest.digest();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("摘要不可用");
    }
  }

  private String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
