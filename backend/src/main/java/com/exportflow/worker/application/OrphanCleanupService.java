// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[ArtifactCleanupConcurrencyTest.java](../../../../../../test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java)、[ArtifactCleanupRaceTest.java](../../../../../../test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java)。
package com.exportflow.worker.application;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.infrastructure.persistence.ArtifactMapper;
import com.exportflow.exporttasks.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 磁盘扫描只登记候选，删除须DB permit且单文件；设计 excel-design.md，TC-236。 */
@Service
public class OrphanCleanupService {
  private final ArtifactRepository artifacts;
  private final ArtifactMapper mapper;
  private final ExportFileStorage storage;
  private final ObjectMapper json;
  private final Path root;
  private long scanOffset;

  public OrphanCleanupService(
      ArtifactRepository artifacts,
      ArtifactMapper mapper,
      ExportFileStorage storage,
      ObjectMapper json,
      RuntimeProperties properties) {
    this.artifacts = artifacts;
    this.mapper = mapper;
    this.storage = storage;
    this.json = json;
    root = properties.getFileRoot().toAbsolutePath().normalize();
  }

  public synchronized int scanCandidates(int limit) throws java.io.IOException {
    if (limit < 1 || limit > 20) throw new IllegalArgumentException("清理批量无效");
    int deleted = 0;
    for (var row : mapper.findCandidates(limit)) {
      var ref = json.readValue(row.storageRef(), StorageRef.class);
      var candidate =
          new StorageCandidate(
              UUID.fromString(row.candidateId()),
              ref,
              UUID.fromString(row.taskId()),
              row.ownerId(),
              row.fence(),
              row.observedAt().toInstant(ZoneOffset.UTC));
      var permit = artifacts.claimCleanup(candidate, Instant.now());
      if (permit.isPresent() && deleteWithPermit(permit.get())) {
        artifacts.completeCleanup(permit.get());
        deleted++;
      }
    }
    if (deleted >= limit || !Files.isDirectory(root.resolve("tasks"), LinkOption.NOFOLLOW_LINKS))
      return deleted;
    int observed = 0;
    long visited = 0;
    int scanBudget = limit - deleted;
    try (var paths = Files.walk(root)) {
      var iterator = paths.iterator();
      while (iterator.hasNext() && observed < scanBudget) {
        Path path = iterator.next();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
        StorageRef ref = parse(path);
        if (ref == null) continue;
        if (visited++ < scanOffset) continue;
        Instant modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        var candidate =
            new StorageCandidate(
                UUID.randomUUID(), ref, ref.taskId(), ref.owner(), ref.attemptFence(), modified);
        if (!storage.resolve(ref).equals(path.toAbsolutePath().normalize())) continue;
        var permit = artifacts.claimCleanup(candidate, Instant.now());
        observed++;
        if (permit.isPresent() && deleteWithPermit(permit.get())) {
          artifacts.completeCleanup(permit.get());
          deleted++;
        }
      }
      scanOffset = iterator.hasNext() ? visited : 0;
    }
    return deleted;
  }

  public boolean deleteWithPermit(CleanupPermit permit) throws java.io.IOException {
    var candidate = mapper.lockCandidate(json.writeValueAsString(permit.ref()));
    if (candidate == null
        || !candidate.candidateId().equals(permit.candidateId().toString())
        || !"CLEANING".equals(candidate.state())
        || !permit.token().toString().equals(candidate.cleanupToken())) return false;
    Path path = storage.resolve(permit.ref());
    Files.deleteIfExists(path);
    return true;
  }

  private StorageRef parse(Path path) {
    try {
      Path relative = root.relativize(path);
      if (relative.getNameCount() == 5 && relative.getName(0).toString().equals("scratch")) {
        UUID process = UUID.fromString(relative.getName(1).toString());
        UUID task = UUID.fromString(relative.getName(2).toString());
        String attempt = relative.getName(3).toString(), file = relative.getFileName().toString();
        int dash = attempt.indexOf('-');
        if (dash < 0 || !file.endsWith(".tmp")) return null;
        return new StorageRef(
            "SCRATCH",
            task,
            UUID.fromString(file.substring(0, file.length() - 4)),
            null,
            Long.parseLong(attempt.substring(0, dash)),
            process + "_" + attempt.substring(dash + 1));
      }
      if (relative.getNameCount() < 4 || !relative.getName(0).toString().equals("tasks"))
        return null;
      UUID task = UUID.fromString(relative.getName(1).toString());
      String file = relative.getFileName().toString();
      if (relative.getNameCount() == 4
          && relative.getName(2).toString().equals("artifacts")
          && file.endsWith(".xlsx"))
        return new StorageRef(
            "ARTIFACT",
            task,
            UUID.fromString(file.substring(0, file.length() - 5)),
            null,
            null,
            null);
      if (relative.getNameCount() == 6
          && relative.getName(2).toString().equals("data")
          && relative.getName(4).toString().equals("blocks")
          && file.endsWith(".jsonl"))
        return new StorageRef(
            "BLOCK",
            task,
            UUID.fromString(file.substring(0, file.length() - 6)),
            Long.parseLong(relative.getName(3).toString()),
            null,
            null);
      if (relative.getNameCount() == 6
          && relative.getName(2).toString().equals("data")
          && relative.getName(4).toString().equals("blocks")
          && file.endsWith(".jsonl.part"))
        return new StorageRef(
            "BLOCK_PART",
            task,
            UUID.fromString(file.substring(0, file.length() - 11)),
            Long.parseLong(relative.getName(3).toString()),
            null,
            null);
      if (relative.getNameCount() == 6
          && relative.getName(2).toString().equals("attempts")
          && relative.getName(4).toString().equals("staging")
          && file.endsWith(".xlsx.part")) {
        String identity = relative.getName(3).toString();
        int dash = identity.indexOf('-');
        return new StorageRef(
            "STAGING",
            task,
            UUID.fromString(file.substring(0, file.length() - 10)),
            null,
            Long.parseLong(identity.substring(0, dash)),
            identity.substring(dash + 1));
      }
    } catch (IllegalArgumentException malformed) {
      return null;
    }
    return null;
  }
}
