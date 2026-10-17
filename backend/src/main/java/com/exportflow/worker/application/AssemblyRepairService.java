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

  public AssemblyRepairService(
      ExportFileStorage storage,
      DataBlockCodec codec,
      AssemblyRepairRepository repairs,
      ExportDataCollectionService collection) {
    this.storage = storage;
    this.codec = codec;
    this.repairs = repairs;
    this.collection = collection;
  }

  /** 对整个封存集合完成审计，再形成可信行数，不因遇到第一个坏段提前停止。 */
  public AssemblyAudit audit(
      GenerationContext context, RepairSnapshot snapshot, RepairIssue trigger, ExecutionGuard guard)
      throws IOException {
    if (!guard.check()) throw new ExecutionStoppedException();
    List<SegmentRef> healthy = new ArrayList<>(), damaged = new ArrayList<>();
    long trusted = 0;
    if (!validManifest(context, snapshot))
      return new AssemblyAudit(snapshot, trigger, healthy, damaged, 0, "DATA_REPAIR_UNRECOVERABLE");
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
    return rows == snapshot.totalCount()
        && manifestDigest(snapshot.segments().stream().map(SegmentRef::baseline).toList())
            .equals(snapshot.manifestDigest());
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
