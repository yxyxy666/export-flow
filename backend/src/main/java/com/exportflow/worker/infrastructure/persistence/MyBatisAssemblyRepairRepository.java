// 本次增量：[计划](../../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[选择性修复事务](../../../../../../../../../specs/004-task-management/contracts/assembly-repair.md)。
package com.exportflow.worker.infrastructure.persistence;

import static com.exportflow.worker.domain.AssemblyRepairModels.*;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.progress.port.ProgressRepository;
import com.exportflow.worker.domain.ExecutionModels.ExecutionLease;
import com.exportflow.worker.port.AssemblyRepairRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class MyBatisAssemblyRepairRepository implements AssemblyRepairRepository {
  private final AssemblyRepairMapper mapper;
  private final ExportTaskRepository tasks;
  private final TaskExecutionMapper executions;
  private final CheckpointMapper checkpoints;
  private final CommandOutboxMapper commands;
  private final ProgressRepository progress;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;

  public MyBatisAssemblyRepairRepository(
      AssemblyRepairMapper mapper,
      ExportTaskRepository tasks,
      TaskExecutionMapper executions,
      CheckpointMapper checkpoints,
      CommandOutboxMapper commands,
      ProgressRepository progress,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.tasks = tasks;
    this.executions = executions;
    this.checkpoints = checkpoints;
    this.commands = commands;
    this.progress = progress;
    this.json = json;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public RepairSnapshot load(UUID id) {
    var row = mapper.snapshot(id.toString());
    if (row == null) return null;
    try {
      List<SegmentRef> segments =
          row.segmentsJson() == null
              ? List.of()
              : json.readValue(row.segmentsJson(), new TypeReference<List<SegmentRef>>() {});
      return new RepairSnapshot(
          id,
          row.dataGeneration(),
          row.checkpointVersion(),
          row.manifestVersion(),
          row.repairEpoch(),
          row.manifestSealed(),
          row.segmentCount(),
          row.manifestRowCount(),
          row.manifestDigest(),
          row.configurationHash(),
          row.totalCount(),
          row.repairAttempt(),
          segments.stream().sorted(Comparator.comparingInt(SegmentRef::sequence)).toList());
    } catch (java.io.IOException malformed) {
      throw new InvalidExportDataException("DATA_REPAIR_UNRECOVERABLE");
    }
  }

  /** 固定activity→task→command→execution→checkpoint锁序；I/O不进入事务。 */
  private RepairSnapshot lock(ExecutionLease lease) {
    var task = tasks.lockTask(lease.taskId());
    commands.lockByTask(lease.taskId().toString());
    var execution = executions.lock(lease.taskId().toString());
    checkpoints.lock(lease.taskId().toString());
    if (task == null
        || task.status() != TaskStatus.RUNNING
        || task.controlVersion() != lease.controlVersion()
        || execution == null
        || !execution.accepts(lease, commands.databaseNow())) return null;
    return load(lease.taskId());
  }

  private boolean same(RepairSnapshot a, RepairSnapshot b) {
    return a != null
        && b != null
        && a.taskId().equals(b.taskId())
        && a.dataGeneration() == b.dataGeneration()
        && a.checkpointVersion() == b.checkpointVersion()
        && a.manifestVersion() == b.manifestVersion()
        && a.repairEpoch() == b.repairEpoch()
        && a.segments().equals(b.segments());
  }

  private RepairWriteResult result(RepairWriteKind kind) {
    return new RepairWriteResult(kind, null);
  }

  public RepairWriteResult markChecking(ExecutionLease lease) {
    return transaction.execute(
        status -> {
          if (lock(lease) == null) return result(RepairWriteKind.STALE);
          mapper.checking(lease.taskId().toString());
          changed(lease.taskId());
          return result(RepairWriteKind.APPLIED);
        });
  }

  public RepairWriteResult sealManifest(
      ExecutionLease lease, RepairSnapshot expected, List<SegmentBaseline> baselines) {
    return transaction.execute(
        status -> {
          var current = lock(lease);
          if (!same(current, expected)) return result(RepairWriteKind.STALE);
          if (current.manifestSealed()) return result(RepairWriteKind.REPLAY);
          var checkpoint = checkpoints.lock(lease.taskId().toString());
          if (checkpoint == null
              || checkpoint.verifiedPrefixCount() != current.totalCount()
              || baselines.isEmpty()
              || baselines.stream().mapToLong(SegmentBaseline::rowCount).sum()
                  != current.totalCount()) return result(RepairWriteKind.FATAL);
          var selected =
              checkpoints.findBlocks(lease.taskId().toString(), current.dataGeneration());
          if (selected.size() != baselines.size()) return result(RepairWriteKind.FATAL);
          var segments = mapper.lockSegments(lease.taskId().toString(), current.dataGeneration());
          for (int index = 0; index < baselines.size(); index++) {
            var baseline = baselines.get(index);
            var block = selected.get(index);
            if (baseline.sequence() != index + 1
                || block.sequence() != baseline.sequence()
                || baseline.rowCount() != block.rowCount()
                || !baseline.configurationHash().equals(current.configurationHash())
                || baseline.formatVersion() != 1
                || !baseline.contentChecksum().matches("[0-9a-f]{64}"))
              return result(RepairWriteKind.FATAL);
            var existing =
                segments.stream()
                    .filter(item -> item.sequence() == baseline.sequence())
                    .findFirst();
            if (existing.isEmpty())
              mapper.insertSegment(
                  segmentRow(
                      current.taskId(), current.dataGeneration(), baseline, block.blockId()));
            else if (!existing.get().contentChecksum().equals(baseline.contentChecksum())
                || !Objects.equals(existing.get().activeBlockId(), block.blockId()))
              return result(RepairWriteKind.FATAL);
          }
          mapper.seal(
              lease.taskId().toString(),
              baselines.size(),
              current.totalCount(),
              manifestDigest(baselines));
          mapper.phase(lease.taskId().toString(), current.totalCount(), "CHECKING", "CHECKING");
          changed(lease.taskId());
          return result(RepairWriteKind.APPLIED);
        });
  }

  public RepairWriteResult beginRepair(ExecutionLease lease, AssemblyAudit audit) {
    return transaction.execute(
        status -> {
          var current = lock(lease);
          if (!same(current, audit.snapshot())) return result(RepairWriteKind.STALE);
          if (audit.fatalReason() != null) return result(RepairWriteKind.FATAL);
          if (audit.damagedSegments().isEmpty() && audit.triggerIssue() == null)
            return result(RepairWriteKind.STALE);
          String roundState = mapper.lockRepair(lease.taskId().toString(), current.repairEpoch());
          var segments = mapper.lockSegments(lease.taskId().toString(), current.dataGeneration());
          // 崩溃后的未完成轮次沿用原预算、epoch和待修段；不能每次接管都重新计数。
          if ("REBUILDING".equals(roundState) && !audit.damagedSegments().isEmpty()) {
            boolean resumable = true;
            for (var segment : audit.damagedSegments()) {
              var pending =
                  mapper.lockReplacement(
                      lease.taskId().toString(), current.repairEpoch(), segment.sequence());
              if (pending == null
                  || !"PENDING".equals(pending.state())
                  || pending.expectedSelectionVersion() != segment.selectionVersion())
                resumable = false;
            }
            if (resumable)
              return new RepairWriteResult(
                  RepairWriteKind.REPLAY,
                  new RepairPlan(
                      current.taskId(),
                      current.dataGeneration(),
                      current.checkpointVersion(),
                      current.manifestVersion(),
                      current.repairEpoch(),
                      current.repairAttempt(),
                      audit.damagedSegments()));
          }
          if (current.repairAttempt() >= 3) return result(RepairWriteKind.EXHAUSTED);
          String reason =
              audit.triggerIssue() != null ? audit.triggerIssue().code() : "DATA_BLOCK_CORRUPT";
          long epoch = current.repairEpoch() + 1;
          int attempt = current.repairAttempt() + 1;
          mapper.begin(lease.taskId().toString(), audit.trustedRows(), reason);
          mapper.insertRepair(
              lease.taskId().toString(),
              epoch,
              attempt,
              current.checkpointVersion(),
              current.manifestVersion(),
              reason);
          for (var segment : audit.damagedSegments()) {
            var old =
                segments.stream()
                    .filter(row -> row.sequence() == segment.sequence())
                    .findFirst()
                    .orElseThrow();
            mapper.planSegment(
                lease.taskId().toString(),
                epoch,
                segment.sequence(),
                old.activeBlockId(),
                segment.selectionVersion());
            mapper.auditSegment(
                lease.taskId().toString(),
                current.dataGeneration(),
                segment.sequence(),
                epoch,
                "REBUILD_REQUIRED");
          }
          for (var healthy : audit.healthySegments())
            mapper.auditSegment(
                lease.taskId().toString(),
                current.dataGeneration(),
                healthy.sequence(),
                epoch,
                "VERIFIED");
          changed(lease.taskId());
          return new RepairWriteResult(
              RepairWriteKind.APPLIED,
              new RepairPlan(
                  current.taskId(),
                  current.dataGeneration(),
                  current.checkpointVersion(),
                  current.manifestVersion(),
                  epoch,
                  attempt,
                  audit.damagedSegments()));
        });
  }

  public RepairWriteResult commitReplacement(
      ExecutionLease lease, RepairPlan plan, SegmentRef segment, VerifiedBlock block) {
    return transaction.execute(
        status -> {
          var current = lock(lease);
          if (current == null
              || !current.taskId().equals(plan.taskId())
              || current.dataGeneration() != plan.dataGeneration()
              || current.checkpointVersion() != plan.checkpointVersion()
              || current.manifestVersion() != plan.manifestVersion()
              || current.repairEpoch() != plan.epoch()) return result(RepairWriteKind.STALE);
          mapper.lockRepair(lease.taskId().toString(), plan.epoch());
          var pending =
              mapper.lockReplacement(lease.taskId().toString(), plan.epoch(), segment.sequence());
          var selected =
              mapper.lockSegments(lease.taskId().toString(), current.dataGeneration()).stream()
                  .filter(row -> row.sequence() == segment.sequence())
                  .findFirst()
                  .orElse(null);
          var ref = block.ref();
          var header = ref.header();
          if (pending == null
              || selected == null
              || !block.contentChecksum().equals(segment.contentChecksum())
              || !new SegmentBaseline(
                      header.sequence(),
                      header.previousCursor(),
                      header.lastCursor(),
                      header.rowCount(),
                      header.configHash(),
                      header.version(),
                      block.contentChecksum())
                  .equals(segment.baseline())
              || header.generation() != current.dataGeneration()
              || !ref.storageRef().taskId().equals(lease.taskId())
              || !ref.blockId().equals(ref.storageRef().identity())
              || !"BLOCK".equals(ref.storageRef().kind())
              || !Objects.equals(ref.storageRef().generation(), current.dataGeneration())
              || ref.byteSize() < 1
              || !ref.checksum().matches("[0-9a-f]{64}")) return result(RepairWriteKind.STALE);
          if ("REPLACED".equals(pending.state()))
            return result(
                ref.blockId().toString().equals(pending.replacementBlockId())
                        && ref.blockId().toString().equals(selected.activeBlockId())
                    ? RepairWriteKind.REPLAY
                    : RepairWriteKind.STALE);
          if (pending.expectedSelectionVersion() != segment.selectionVersion()
              || selected.selectionVersion() != segment.selectionVersion()
              || !"REBUILD_REQUIRED".equals(selected.state())) return result(RepairWriteKind.STALE);
          checkpoints.insertBlock(blockRecord(ref, lease));
          if (mapper.selectBlock(
                  lease.taskId().toString(),
                  current.dataGeneration(),
                  segment.sequence(),
                  segment.selectionVersion(),
                  ref.blockId().toString(),
                  plan.epoch())
              != 1) throw new IllegalStateException("块选择CAS失效");
          mapper.replaced(
              lease.taskId().toString(),
              plan.epoch(),
              segment.sequence(),
              ref.blockId().toString());
          if (mapper.addTrusted(lease.taskId().toString(), segment.rowCount()) != 1)
            throw new IllegalStateException("可信计数无效");
          changed(lease.taskId());
          return result(RepairWriteKind.APPLIED);
        });
  }

  public Optional<VerifiedBlock> findReplacement(UUID id, long epoch, int sequence) {
    var pending = mapper.lockReplacement(id.toString(), epoch, sequence);
    if (pending == null || !"REPLACED".equals(pending.state())) return Optional.empty();
    var snapshot = load(id);
    return snapshot.segments().stream()
        .filter(
            segment ->
                segment.sequence() == sequence
                    && segment.selectedBlock() != null
                    && segment
                        .selectedBlock()
                        .blockId()
                        .toString()
                        .equals(pending.replacementBlockId()))
        .map(segment -> new VerifiedBlock(segment.selectedBlock(), segment.contentChecksum()))
        .findFirst();
  }

  public RepairWriteResult markAssembling(ExecutionLease lease, AssemblyAudit audit) {
    return transaction.execute(
        status -> {
          var current = lock(lease);
          if (!same(current, audit.snapshot())
              || audit.fatalReason() != null
              || !audit.damagedSegments().isEmpty()
              || audit.trustedRows() != current.totalCount()) return result(RepairWriteKind.STALE);
          mapper.lockRepair(lease.taskId().toString(), current.repairEpoch());
          mapper.lockSegments(lease.taskId().toString(), current.dataGeneration());
          mapper.phase(
              lease.taskId().toString(),
              audit.trustedRows(),
              "ASSEMBLING",
              current.repairAttempt() > 0 ? "REBUILDING" : "NONE");
          mapper.assembling(lease.taskId().toString(), current.repairEpoch());
          changed(lease.taskId());
          return result(RepairWriteKind.APPLIED);
        });
  }

  /** 审计快照仍有效且全部登记前缀可信才放行收集，不把历史高水位充作可信量。 */
  public RepairWriteResult markCollecting(ExecutionLease lease, AssemblyAudit audit) {
    return transaction.execute(
        status -> {
          var current = lock(lease);
          if (!same(current, audit.snapshot())
              || current.manifestSealed()
              || audit.fatalReason() != null
              || !audit.damagedSegments().isEmpty()) return result(RepairWriteKind.STALE);
          var checkpoint = checkpoints.lock(lease.taskId().toString());
          if (checkpoint == null || checkpoint.verifiedPrefixCount() != audit.trustedRows())
            return result(RepairWriteKind.STALE);
          mapper.phase(
              lease.taskId().toString(),
              audit.trustedRows(),
              "COLLECTING",
              current.repairAttempt() > 0 ? "REBUILDING" : "NONE");
          changed(lease.taskId());
          return result(RepairWriteKind.APPLIED);
        });
  }

  private void changed(UUID id) {
    progress.recordChange(id, tasks.lockTask(id).version());
  }

  public AssemblyRepairMapper.SegmentRow segmentRow(
      UUID id, long generation, SegmentBaseline baseline, String blockId) {
    return new AssemblyRepairMapper.SegmentRow(
        id.toString(),
        generation,
        baseline.sequence(),
        encode(baseline.previousCursor()),
        encode(baseline.lastCursor()),
        baseline.rowCount(),
        baseline.configurationHash(),
        baseline.formatVersion(),
        baseline.contentChecksum(),
        blockId,
        0,
        0,
        "VERIFIED");
  }

  private CheckpointRecord.BlockRecord blockRecord(BlockRef ref, ExecutionLease lease) {
    var h = ref.header();
    return new CheckpointRecord.BlockRecord(
        ref.blockId().toString(),
        lease.taskId().toString(),
        h.generation(),
        h.sequence(),
        encode(h.previousCursor()),
        encode(h.lastCursor()),
        h.rowCount(),
        ref.byteSize(),
        ref.checksum(),
        encode(ref.storageRef()),
        h.version(),
        h.configHash(),
        lease.owner(),
        lease.fence());
  }

  private String encode(Object value) {
    if (value == null) return null;
    try {
      return json.writeValueAsString(value);
    } catch (java.io.IOException invalid) {
      throw new InvalidExportDataException();
    }
  }
}
