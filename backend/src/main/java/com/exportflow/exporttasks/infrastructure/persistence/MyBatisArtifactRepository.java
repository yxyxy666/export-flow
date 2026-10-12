// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[ArtifactCleanupConcurrencyTest.java](../../../../../../../test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java)、[ArtifactCleanupRaceTest.java](../../../../../../../test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.infrastructure.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 成功引用永久保护，清理与采用串行；设计 function-design-reference.md，TC-236。 */
@Repository
public class MyBatisArtifactRepository implements ArtifactRepository {
  private final ArtifactMapper artifacts;
  private final ExportTaskRepository tasks;
  private final TaskExecutionMapper executions;
  private final CheckpointMapper checkpoints;
  private final CommandOutboxMapper clock;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;

  public MyBatisArtifactRepository(
      ArtifactMapper artifacts,
      ExportTaskRepository tasks,
      TaskExecutionMapper executions,
      CheckpointMapper checkpoints,
      CommandOutboxMapper clock,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.artifacts = artifacts;
    this.tasks = tasks;
    this.executions = executions;
    this.checkpoints = checkpoints;
    this.clock = clock;
    this.json = json;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public Optional<ArtifactProof> findReady(UUID taskId, String hash) {
    return artifacts
        .findReady(taskId.toString(), hash)
        .map(
            row -> {
              if (!row.configurationHash().equals(hash))
                throw new InvalidExportDataException("EXPORT_FILE_INVALID");
              return proof(row);
            });
  }

  public Optional<ArtifactProof> findAdopted(UUID taskId) {
    return artifacts.findAdopted(taskId.toString()).map(this::proof);
  }

  public LeaseWriteResult registerReady(ExecutionLease lease, ArtifactProof proof) {
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(lease.taskId());
          var execution = executions.lock(lease.taskId().toString());
          var checkpoint = checkpoints.lock(lease.taskId().toString());
          var now = clock.databaseNow();
          var snapshot = tasks.findSnapshot(lease.taskId()).orElse(null);
          if (task == null
              || task.status() != TaskStatus.RUNNING
              || execution == null
              || !execution.accepts(lease, now)
              || checkpoint == null
              || snapshot == null
              || !proof.configurationHash().equals(snapshot.configurationHash())
              || !proof.fileName().equals(snapshot.fileName())
              || !proof.taskId().equals(lease.taskId())
              || proof.rowCount() != task.totalCount()
              || proof.rowCount() != checkpoint.verifiedPrefixCount()
              || proof.dataGeneration() != checkpoint.dataGeneration()
              || !proof.configurationHash().equals(checkpoint.configurationHash())
              || proof.size() < 1
              || !proof.checksum().matches("[0-9a-f]{64}")
              || !"ARTIFACT".equals(proof.artifactRef().kind())
              || !proof.artifactId().equals(proof.artifactRef().identity())
              || !proof.taskId().equals(proof.artifactRef().taskId())
              || proof.artifactRef().generation() != null
              || proof.artifactRef().attemptFence() != null
              || proof.artifactRef().owner() != null) return LeaseWriteResult.STALE;
          var existing = artifacts.lockArtifact(proof.artifactId().toString());
          var candidate = artifacts.lockCandidate(encode(proof.artifactRef()));
          if (candidate != null && !"OBSERVED".equals(candidate.state()))
            return LeaseWriteResult.STALE;
          if (existing != null)
            return "READY".equals(existing.state()) && proof(existing).equals(proof)
                ? LeaseWriteResult.APPLIED
                : LeaseWriteResult.STALE;
          artifacts.insertReady(
              new ArtifactRecord(
                  proof.artifactId().toString(),
                  proof.taskId().toString(),
                  proof.dataGeneration(),
                  proof.configurationHash(),
                  encode(proof.artifactRef()),
                  proof.rowCount(),
                  proof.size(),
                  proof.checksum(),
                  proof.fileName(),
                  "READY",
                  lease.owner(),
                  lease.fence(),
                  LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                  null,
                  null));
          return LeaseWriteResult.APPLIED;
        });
  }

  public Optional<CleanupPermit> claimCleanup(StorageCandidate candidate, Instant ignoredHostTime) {
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(candidate.taskId());
          var execution = executions.lock(candidate.taskId().toString());
          checkpoints.lock(candidate.taskId().toString());
          if (task == null || !candidate.taskId().equals(candidate.ref().taskId()))
            return Optional.empty();
          if ("ARTIFACT".equals(candidate.ref().kind()))
            artifacts.lockArtifact(candidate.ref().identity().toString());
          var now = clock.databaseNow();
          String ref = encode(candidate.ref());
          artifacts.insertCandidate(
              new ArtifactMapper.CleanupCandidateRecord(
                  candidate.id().toString(),
                  ref,
                  candidate.taskId().toString(),
                  candidate.owner(),
                  candidate.fence(),
                  candidate.ref().kind(),
                  LocalDateTime.ofInstant(candidate.observedAt(), ZoneOffset.UTC),
                  "OBSERVED",
                  null));
          var row = artifacts.lockCandidate(ref);
          if (row == null
              || !Set.of("OBSERVED", "CLEANING").contains(row.state())
              || row.observedAt().toInstant(ZoneOffset.UTC).plus(Duration.ofHours(24)).isAfter(now)
              || artifacts.blockReferences(ref) > 0
              || artifacts.artifactReferences(ref) > 0) return Optional.empty();
          if (execution != null
              && execution.accepts(execution.toLease(), now)
              && (candidate.fence() == null || candidate.fence() == execution.fence()))
            return Optional.empty();
          if ("CLEANING".equals(row.state()))
            return Optional.of(
                new CleanupPermit(
                    UUID.fromString(row.candidateId()),
                    UUID.fromString(row.cleanupToken()),
                    candidate.ref()));
          UUID token = UUID.randomUUID();
          if (artifacts.claimCleanup(row.candidateId(), token.toString()) != 1)
            return Optional.empty();
          return Optional.of(
              new CleanupPermit(UUID.fromString(row.candidateId()), token, candidate.ref()));
        });
  }

  public void completeCleanup(CleanupPermit permit) {
    transaction.execute(
        status -> {
          tasks.lockTask(permit.ref().taskId());
          var candidate = artifacts.lockCandidate(encode(permit.ref()));
          if (candidate != null && candidate.candidateId().equals(permit.candidateId().toString()))
            artifacts.completeCleanup(candidate.candidateId(), permit.token().toString());
          return null;
        });
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (java.io.IOException invalid) {
      throw new InvalidExportDataException();
    }
  }

  private ArtifactProof proof(ArtifactRecord row) {
    try {
      return new ArtifactProof(
          UUID.fromString(row.artifactId()),
          UUID.fromString(row.taskId()),
          row.dataGeneration(),
          row.configurationHash(),
          json.readValue(row.storageRef(), StorageRef.class),
          row.fileName(),
          row.byteSize(),
          row.rowCount(),
          row.checksum());
    } catch (java.io.IOException invalid) {
      throw new InvalidExportDataException();
    }
  }
}
