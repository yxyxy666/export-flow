// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import static com.exportflow.worker.domain.ExecutionModels.*;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.infrastructure.persistence.ArtifactMapper;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.worker.port.TaskExecutionRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 共同task→outbox→execution→artifact锁序下裁决；设计 function-design-reference.md，TC-224。 */
@Repository
public class MyBatisTaskExecutionRepository implements TaskExecutionRepository {
  private final ExportTaskRepository tasks;
  private final CommandOutboxMapper outbox;
  private final TaskExecutionMapper executions;
  private final ArtifactMapper artifacts;
  private final CheckpointMapper checkpoints;
  private final com.fasterxml.jackson.databind.ObjectMapper json;
  private final TransactionTemplate transaction;

  public MyBatisTaskExecutionRepository(
      ExportTaskRepository tasks,
      CommandOutboxMapper outbox,
      TaskExecutionMapper executions,
      ArtifactMapper artifacts,
      CheckpointMapper checkpoints,
      com.fasterxml.jackson.databind.ObjectMapper json,
      PlatformTransactionManager manager) {
    this.tasks = tasks;
    this.outbox = outbox;
    this.executions = executions;
    this.artifacts = artifacts;
    this.checkpoints = checkpoints;
    this.json = json;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public ClaimResult claim(UUID taskId, String owner, ExecutionPolicy policy) {
    if (owner == null || owner.isBlank() || owner.length() > 128)
      throw new IllegalArgumentException("执行身份无效");
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(taskId);
          if (task == null) return new ClaimResult(ClaimKind.UNKNOWN, null, null);
          var command = outbox.lockByTask(taskId.toString());
          var record = executions.lock(taskId.toString());
          Instant now = outbox.databaseNow();
          if (task.status() == TaskStatus.SUCCEEDED || task.status() == TaskStatus.FAILED)
            return new ClaimResult(
                ClaimKind.TERMINAL, null, tasks.findSnapshot(taskId).orElseThrow());
          if (task.status() == TaskStatus.PENDING) {
            if (command == null || "CLOSED".equals(command.status()))
              return new ClaimResult(ClaimKind.UNKNOWN, null, null);
            if (!"SENT".equals(command.status())
                && !now.isBefore(command.dispatchDeadline().toInstant(ZoneOffset.UTC))) {
              tasks.failDispatchTimeout(taskId, now);
              outbox.close(command.eventId(), "TASK_DISPATCH_TIMEOUT", now);
              return new ClaimResult(
                  ClaimKind.TERMINAL, null, tasks.findSnapshot(taskId).orElseThrow());
            }
            executions.markRunning(taskId.toString());
            Instant until = now.plus(policy.leaseDuration());
            executions.insertLease(
                taskId.toString(),
                owner,
                now,
                until,
                now.plus(policy.maxAttemptDuration()),
                until.plus(backoff(0, policy)));
          } else {
            if (record == null) return new ClaimResult(ClaimKind.UNKNOWN, null, null);
            if (now.isBefore(record.leaseUntil().toInstant(ZoneOffset.UTC))
                || now.isBefore(record.nextRecoveryAt().toInstant(ZoneOffset.UTC)))
              return new ClaimResult(
                  ClaimKind.BUSY, null, tasks.findSnapshot(taskId).orElseThrow());
            if (record.recoveryCount() >= policy.maxRecoveryTakeovers()) {
              executions.completeTask(
                  taskId.toString(),
                  "FAILED",
                  now,
                  null,
                  null,
                  null,
                  "EXPORT_RECOVERY_EXHAUSTED",
                  "任务异常恢复次数已耗尽");
              executions.releaseLease(taskId.toString(), now);
              return new ClaimResult(
                  ClaimKind.TERMINAL, null, tasks.findSnapshot(taskId).orElseThrow());
            }
            Instant until = now.plus(policy.leaseDuration());
            executions.takeOver(
                taskId.toString(),
                owner,
                now,
                until,
                now.plus(policy.maxAttemptDuration()),
                until.plus(backoff(record.recoveryCount() + 1, policy)));
          }
          return new ClaimResult(
              ClaimKind.ACQUIRED,
              executions.find(taskId.toString()).toLease(),
              tasks.findSnapshot(taskId).orElseThrow());
        });
  }

  private Duration backoff(int count, ExecutionPolicy policy) {
    long cap =
        Math.min(
            policy.recoveryBackoffCap().toMillis(),
            policy.recoveryBackoffBase().toMillis() * (1L << Math.min(count, 20)));
    return Duration.ofMillis(ThreadLocalRandom.current().nextLong(cap + 1));
  }

  public LeaseWriteResult renew(ExecutionLease lease) {
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(lease.taskId());
          outbox.lockByTask(lease.taskId().toString());
          var record = executions.lock(lease.taskId().toString());
          Instant now = outbox.databaseNow();
          if (task == null
              || task.status() != TaskStatus.RUNNING
              || record == null
              || !record.accepts(lease, now)) return LeaseWriteResult.STALE;
          Instant limit = record.attemptDeadline().toInstant(ZoneOffset.UTC),
              until = now.plusSeconds(30);
          if (until.isAfter(limit)) until = limit;
          executions.renew(
              lease.taskId().toString(),
              now,
              until,
              until.plus(backoff(record.recoveryCount(), ExecutionPolicy.defaults())));
          return LeaseWriteResult.APPLIED;
        });
  }

  public LeaseWriteResult complete(ExecutionLease lease, GenerationResult result) {
    if (result == null || result.kind() == GenerationKind.UNKNOWN) return LeaseWriteResult.UNKNOWN;
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(lease.taskId());
          outbox.lockByTask(lease.taskId().toString());
          var record = executions.lock(lease.taskId().toString());
          Instant now = outbox.databaseNow();
          if (task == null
              || task.status() != TaskStatus.RUNNING
              || record == null
              || !record.accepts(lease, now)) return LeaseWriteResult.STALE;
          if (result.kind() == GenerationKind.READY) {
            var proof = result.artifact();
            if (proof == null
                || !proof.taskId().equals(lease.taskId())
                || proof.rowCount() != task.totalCount()
                || task.processedCount() != task.totalCount()) return LeaseWriteResult.STALE;
            var snapshot = tasks.findSnapshot(lease.taskId()).orElseThrow();
            var checkpoint = checkpoints.lock(lease.taskId().toString());
            if (checkpoint == null
                || checkpoint.verifiedPrefixCount() != task.totalCount()
                || checkpoint.dataGeneration() != proof.dataGeneration()
                || !proof.configurationHash().equals(snapshot.configurationHash())
                || !proof.configurationHash().equals(checkpoint.configurationHash())
                || !proof.fileName().equals(snapshot.fileName())) return LeaseWriteResult.STALE;
            var artifact = artifacts.lockArtifact(proof.artifactId().toString());
            if (artifact == null
                || !"READY".equals(artifact.state())
                || artifact.cleanupToken() != null
                || !artifact.taskId().equals(lease.taskId().toString())
                || !artifact.configurationHash().equals(proof.configurationHash())
                || artifact.byteSize() != proof.size()
                || artifact.rowCount() != proof.rowCount()
                || !artifact.checksum().equals(proof.checksum())
                || artifact.dataGeneration() != proof.dataGeneration()
                || !artifact.fileName().equals(proof.fileName())) return LeaseWriteResult.STALE;
            try {
              if (!json.readValue(
                      artifact.storageRef(),
                      com.exportflow.exporttasks.domain.ExportFileModels.StorageRef.class)
                  .equals(proof.artifactRef())) return LeaseWriteResult.STALE;
            } catch (java.io.IOException malformed) {
              return LeaseWriteResult.STALE;
            }
            if (artifacts.adopt(
                    lease.taskId().toString(), proof.artifactId().toString(), lease.fence())
                != 1) return LeaseWriteResult.STALE;
            executions.completeTask(
                lease.taskId().toString(),
                "SUCCEEDED",
                now,
                proof.artifactId().toString(),
                proof.fileName(),
                proof.size(),
                null,
                null);
          } else {
            if (result.error() == null) return LeaseWriteResult.STALE;
            executions.completeTask(
                lease.taskId().toString(),
                "FAILED",
                now,
                null,
                null,
                null,
                result.error().code(),
                result.error().message());
          }
          executions.releaseLease(lease.taskId().toString(), now);
          return LeaseWriteResult.APPLIED;
        });
  }

  public ClaimResult find(UUID taskId) {
    var snapshot = tasks.findSnapshot(taskId);
    if (snapshot.isEmpty()) return new ClaimResult(ClaimKind.UNKNOWN, null, null);
    var task = snapshot.get().task();
    if (task.status() == TaskStatus.SUCCEEDED || task.status() == TaskStatus.FAILED)
      return new ClaimResult(ClaimKind.TERMINAL, null, snapshot.get());
    var record = executions.find(taskId.toString());
    if (record == null) return new ClaimResult(ClaimKind.BUSY, null, snapshot.get());
    Instant now = outbox.databaseNow();
    return new ClaimResult(
        record.accepts(record.toLease(), now) ? ClaimKind.ACQUIRED : ClaimKind.BUSY,
        record.toLease(),
        snapshot.get());
  }
}
