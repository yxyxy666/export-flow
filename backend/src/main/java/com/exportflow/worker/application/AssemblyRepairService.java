// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[装配审计与选择性重建](../../../../../../../../specs/004-task-management/contracts/assembly-repair.md)。
package com.exportflow.worker.application;

import static com.exportflow.worker.domain.AssemblyRepairModels.*;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import com.exportflow.worker.infrastructure.file.DataBlockCodec;
import com.exportflow.worker.port.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AssemblyRepairService {
  private final ExportFileStorage storage;
  private final DataBlockCodec codec;
  private final AssemblyRepairRepository repairs;
  private final ExportDataCollectionService collection;
  private final CheckpointRepository checkpoints;

  public AssemblyRepairService(
      ExportFileStorage storage,
      DataBlockCodec codec,
      AssemblyRepairRepository repairs,
      ExportDataCollectionService collection,
      CheckpointRepository checkpoints) {
    this.storage = storage;
    this.codec = codec;
    this.repairs = repairs;
    this.collection = collection;
    this.checkpoints = checkpoints;
  }

  /** 对整个封存集合完成审计，再形成可信行数，不因遇到第一个坏段提前停止。 */
  public AssemblyAudit audit(
      GenerationContext context, RepairSnapshot snapshot, RepairIssue trigger, ExecutionGuard guard)
      throws IOException {
    if (!guard.check()) throw new ExecutionStoppedException();
    if (!validManifest(context, snapshot))
      return new AssemblyAudit(
          snapshot, trigger, List.of(), List.of(), 0, "DATA_REPAIR_UNRECOVERABLE");
    return auditBlocks(snapshot, trigger, guard);
  }

  /** 部分收集以持久断点为边界，不能把任务总量当作已完成区间。 */
  public AssemblyAudit auditPrefix(
      GenerationContext context,
      RepairSnapshot snapshot,
      CheckpointSnapshot checkpoint,
      ExecutionGuard guard)
      throws IOException {
    if (!guard.check()) throw new ExecutionStoppedException();
    if (snapshot == null
        || snapshot.manifestSealed()
        || !snapshot.taskId().equals(context.lease().taskId())
        || !snapshot.taskId().equals(checkpoint.taskId())
        || snapshot.dataGeneration() != checkpoint.generation()
        || snapshot.checkpointVersion() != checkpoint.version()
        || !Objects.equals(snapshot.configurationHash(), context.snapshot().configurationHash())
        || !Objects.equals(checkpoint.configurationHash(), snapshot.configurationHash())
        || checkpoint.verifiedPrefixCount() > snapshot.totalCount()
        || !validSegments(snapshot, checkpoint.verifiedPrefixCount(), checkpoint.cursor(), true))
      return new AssemblyAudit(
          snapshot, null, List.of(), List.of(), 0, "DATA_REPAIR_UNRECOVERABLE");
    return auditBlocks(snapshot, null, guard);
  }

  /** 显式继续先审计已提交部分；健康段保留，坏段修复后再放行尾部收集。 */
  public void prepareResume(
      GenerationContext context, AttemptWorkspace workspace, ExecutionGuard guard)
      throws IOException {
    if (context.lease().controlVersion() == 0) return;
    var checkpoint = checkpoints.find(context.lease().taskId());
    var snapshot = repairs.load(context.lease().taskId());
    if (checkpoint.version() == 0) {
      if (context.snapshot().task().processedCount() != 0)
        throw new InvalidExportDataException("DATA_REPAIR_UNRECOVERABLE");
      return;
    }
    if (snapshot != null && snapshot.manifestSealed()) return;
    if (repairs.markChecking(context.lease()).kind() != RepairWriteKind.APPLIED)
      throw new IOException("恢复核验授权尚未确认");
    while (true) {
      var audit = auditPrefix(context, snapshot, checkpoint, guard);
      if (audit.fatalReason() != null) throw new InvalidExportDataException(audit.fatalReason());
      if (audit.damagedSegments().isEmpty()) {
        if (repairs.markCollecting(context.lease(), audit).kind() != RepairWriteKind.APPLIED)
          throw new IOException("恢复核验提交尚未确认");
        return;
      }
      rebuild(context, audit, workspace, guard);
      checkpoint = checkpoints.find(context.lease().taskId());
      snapshot = repairs.load(context.lease().taskId());
    }
  }

  private AssemblyAudit auditBlocks(
      RepairSnapshot snapshot, RepairIssue trigger, ExecutionGuard guard) throws IOException {
    List<SegmentRef> healthy = new ArrayList<>(), damaged = new ArrayList<>();
    long trusted = 0;
    RepairIssue reason = trigger;
    for (var segment : snapshot.segments()) {
      if (!guard.check()) throw new ExecutionStoppedException();
      var block = segment.selectedBlock();
      String problem = null;
      if (block == null) problem = "DATA_BLOCK_RECORD_MISSING";
      else if (!block.storageRef().taskId().equals(snapshot.taskId())
          || block.header().generation() != snapshot.dataGeneration()
          || !"BLOCK".equals(block.storageRef().kind())
          || !Objects.equals(block.storageRef().generation(), snapshot.dataGeneration())
          || !block.blockId().equals(block.storageRef().identity())) problem = "DATA_BLOCK_CORRUPT";
      else
        try (var reader = storage.readBlock(block)) {
          codec.verifyContent(reader, block, segment.baseline(), guard);
        } catch (NoSuchFileException | FileNotFoundException missing) {
          if (missing.getSuppressed().length > 0) throw missing;
          problem = "DATA_BLOCK_MISSING";
        } catch (InvalidExportDataException corrupt) {
          if (corrupt.getSuppressed().length > 0) throw new IOException("资源关闭尚未确认", corrupt);
          problem = "DATA_BLOCK_CORRUPT";
        }
      if (problem == null) {
        healthy.add(segment);
        trusted += segment.rowCount();
      } else {
        damaged.add(segment);
        if (reason == null) reason = new RepairIssue(problem, segment.sequence());
      }
    }
    return new AssemblyAudit(snapshot, reason, healthy, damaged, trusted, null);
  }

  private boolean validManifest(GenerationContext context, RepairSnapshot snapshot) {
    if (snapshot == null
        || !snapshot.manifestSealed()
        || !snapshot.taskId().equals(context.lease().taskId())
        || !Objects.equals(snapshot.configurationHash(), context.snapshot().configurationHash())
        || snapshot.segmentCount() != snapshot.segments().size()
        || snapshot.segments().isEmpty()
        || snapshot.manifestRowCount() != context.snapshot().task().totalCount()) return false;
    return validSegments(snapshot, snapshot.totalCount(), null, false)
        && manifestDigest(snapshot.segments().stream().map(SegmentRef::baseline).toList())
            .equals(snapshot.manifestDigest());
  }

  private boolean validSegments(
      RepairSnapshot snapshot, long expectedRows, OrderCursor lastCursor, boolean checkCursor) {
    OrderCursor previous = null;
    int sequence = 0;
    long rows = 0;
    for (var segment : snapshot.segments()) {
      if (segment.sequence() != ++sequence
          || !Objects.equals(segment.previousCursor(), previous)
          || segment.lastCursor() == null
          || segment.rowCount() < 1
          || segment.rowCount() > 1000
          || segment.formatVersion() != 1
          || !Objects.equals(segment.configurationHash(), snapshot.configurationHash())
          || segment.contentChecksum() == null
          || !segment.contentChecksum().matches("[0-9a-f]{64}")
          || previous != null
              && (segment.lastCursor().createdAt().isAfter(previous.createdAt())
                  || segment.lastCursor().createdAt().equals(previous.createdAt())
                      && segment.lastCursor().id() >= previous.id())) return false;
      rows += segment.rowCount();
      previous = segment.lastCursor();
    }
    return rows == expectedRows && (!checkCursor || Objects.equals(previous, lastCursor));
  }

  /** 使用持久轮次；提交不明通过同epoch/sequence找回，不重复累计可信行数。 */
  public void rebuild(
      GenerationContext context,
      AssemblyAudit audit,
      AttemptWorkspace workspace,
      ExecutionGuard guard)
      throws IOException {
    if (audit.fatalReason() != null) throw new InvalidExportDataException(audit.fatalReason());
    var started = repairs.beginRepair(context.lease(), audit);
    if (started.kind() == RepairWriteKind.EXHAUSTED)
      throw new InvalidExportDataException("DATA_REPAIR_EXHAUSTED");
    if (started.kind() != RepairWriteKind.APPLIED && started.kind() != RepairWriteKind.REPLAY)
      throw new IOException("修复授权尚未确认");
    var plan = started.plan();
    for (var segment : plan.damagedSegments()) {
      if (!guard.check()) throw new ExecutionStoppedException();
      var block = collection.rebuildSegment(context, segment, workspace, guard);
      try {
        var written = repairs.commitReplacement(context.lease(), plan, segment, block);
        if (written.kind() != RepairWriteKind.APPLIED && written.kind() != RepairWriteKind.REPLAY)
          throw new IOException("替换提交尚未确认");
      } catch (RuntimeException unknown) {
        if (repairs
            .findReplacement(plan.taskId(), plan.epoch(), segment.sequence())
            .filter(block::equals)
            .isEmpty()) throw unknown;
      }
    }
  }
}
