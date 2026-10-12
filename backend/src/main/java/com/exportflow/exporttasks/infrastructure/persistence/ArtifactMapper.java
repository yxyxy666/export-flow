// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[ArtifactCleanupConcurrencyTest.java](../../../../../../../test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java)、[ArtifactCleanupRaceTest.java](../../../../../../../test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import java.util.*;
import org.apache.ibatis.annotations.*;

/** 采用和清理同一行锁；设计 function-design-reference.md，TC-236。 */
@Mapper
public interface ArtifactMapper {
  Optional<ArtifactRecord> findReady(@Param("taskId") String taskId, @Param("hash") String hash);

  Optional<ArtifactRecord> findAdopted(@Param("taskId") String taskId);

  Optional<ArtifactRecord> find(@Param("artifactId") String artifactId);

  ArtifactRecord lockArtifact(@Param("artifactId") String artifactId);

  int insertReady(ArtifactRecord record);

  int adopt(
      @Param("taskId") String taskId,
      @Param("artifactId") String artifactId,
      @Param("fence") long fence);

  int insertCandidate(CleanupCandidateRecord record);

  default int registerCandidate(
      com.exportflow.exporttasks.domain.ExportFileModels.StorageCandidate candidate) {
    try {
      var ref = candidate.ref();
      return insertCandidate(
          new CleanupCandidateRecord(
              candidate.id().toString(),
              new com.fasterxml.jackson.databind.ObjectMapper()
                  .findAndRegisterModules()
                  .writeValueAsString(ref),
              candidate.taskId().toString(),
              candidate.owner(),
              candidate.fence(),
              ref.kind(),
              java.time.LocalDateTime.ofInstant(candidate.observedAt(), java.time.ZoneOffset.UTC),
              "OBSERVED",
              null));
    } catch (java.io.IOException invalid) {
      throw new IllegalArgumentException("清理身份无效");
    }
  }

  CleanupCandidateRecord lockCandidate(@Param("ref") String ref);

  int claimCleanup(@Param("id") String id, @Param("token") String token);

  int completeCleanup(@Param("id") String id, @Param("token") String token);

  long blockReferences(@Param("ref") String ref);

  long artifactReferences(@Param("ref") String ref);

  List<CleanupCandidateRecord> findCandidates(@Param("limit") int limit);

  public record CleanupCandidateRecord(
      String candidateId,
      String storageRef,
      String taskId,
      String ownerId,
      Long fence,
      String kind,
      java.time.LocalDateTime observedAt,
      String state,
      String cleanupToken) {}
}
