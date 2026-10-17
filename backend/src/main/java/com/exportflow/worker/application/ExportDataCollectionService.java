// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.application;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.orders.port.OrderRepository;
import com.exportflow.worker.domain.AssemblyRepairModels.*;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import com.exportflow.worker.infrastructure.file.DataBlockCodec;
import com.exportflow.worker.port.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/** 有界批读→落盘→断点提交；IO与批读不持事务。设计 excel-design.md，TC-235。 */
@Service
public class ExportDataCollectionService {
  private final CheckpointRepository checkpoints;
  private final OrderRepository orders;
  private final ExportFileStorage storage;
  private final AssemblyRepairRepository repairs;
  private final DataBlockCodec codec;

  public ExportDataCollectionService(
      CheckpointRepository checkpoints,
      OrderRepository orders,
      ExportFileStorage storage,
      AssemblyRepairRepository repairs,
      DataBlockCodec codec) {
    this.checkpoints = checkpoints;
    this.orders = orders;
    this.storage = storage;
    this.repairs = repairs;
    this.codec = codec;
  }

  public CompletedDataset collect(GenerationContext context, ExecutionGuard guard)
      throws IOException {
    check(guard);
    var task = context.snapshot().task();
    String config = context.snapshot().configurationHash();
    var workspace = storage.prepareAttempt(context);
    var snapshot = checkpoints.find(task.taskId());
    var manifest = repairs.load(task.taskId());
    // 已封存任务只能进入独立清单对账，不能沿旧坏前缀分支全量换代。
    if (manifest != null && manifest.manifestSealed())
      return new CompletedDataset(
          task.taskId(),
          manifest.dataGeneration(),
          config,
          manifest.segments().stream()
              .map(SegmentRef::selectedBlock)
              .filter(Objects::nonNull)
              .toList(),
          task.totalCount());
    try {
      validatePrefix(snapshot, config, guard);
    } catch (InvalidExportDataException corrupted) {
      var prefix =
          new TrustedPrefix(snapshot.generation(), snapshot.version(), List.of(), null, 0, true);
      var restarted = checkpoints.startGeneration(context.lease(), snapshot.version(), prefix);
      if (restarted.kind() != CheckpointWriteKind.APPLIED) throw new IOException("断点重建权未确认");
      snapshot = restarted.snapshot();
    }
    long expectedTotal = task.totalCount();
    while (snapshot.verifiedPrefixCount() < expectedTotal) {
      check(guard);
      var batch =
          orders.readBatch(task.taskId(), context.snapshot().scope(), snapshot.cursor(), 1000);
      if (batch.rows().isEmpty()) break;
      if (snapshot.verifiedPrefixCount() + batch.rows().size() > expectedTotal)
        throw new InvalidExportDataException("EXPORT_SOURCE_CHANGED");
      var header =
          new BlockHeader(
              1,
              config,
              snapshot.generation(),
              snapshot.blocks().size() + 1,
              snapshot.cursor(),
              batch.lastCursor(),
              batch.rows().size());
      check(guard);
      var block = storage.writeBlock(workspace, header, batch.rows());
      check(guard);
      CheckpointWriteResult committed;
      try {
        committed =
            checkpoints.commitBlock(
                context.lease(),
                snapshot,
                new VerifiedBlock(block, codec.checksumRows(batch.rows(), 1)));
      } catch (RuntimeException unknown) {
        if (checkpoints
            .reconcileBlock(task.taskId(), block.blockId())
            .filter(block::equals)
            .isEmpty()) throw unknown;
        committed =
            new CheckpointWriteResult(CheckpointWriteKind.APPLIED, checkpoints.find(task.taskId()));
      }
      if (committed.kind() != CheckpointWriteKind.APPLIED) {
        if (committed.kind() == CheckpointWriteKind.UNKNOWN
            && checkpoints
                .reconcileBlock(task.taskId(), block.blockId())
                .filter(block::equals)
                .isPresent()) snapshot = checkpoints.find(task.taskId());
        else throw new IOException("断点提交尚未确认");
      } else snapshot = committed.snapshot();
      if (batch.isEnd()) break;
    }
    if (snapshot.verifiedPrefixCount() != expectedTotal)
      throw new InvalidExportDataException("EXPORT_SOURCE_CHANGED");
    List<SegmentBaseline> baselines = new ArrayList<>();
    for (var block : snapshot.blocks())
      try (var reader = storage.readBlock(block)) {
        var verified = codec.verifyContent(reader, block, null, guard);
        var h = block.header();
        baselines.add(
            new SegmentBaseline(
                h.sequence(),
                h.previousCursor(),
                h.lastCursor(),
                h.rowCount(),
                h.configHash(),
                h.version(),
                verified.contentChecksum()));
      }
    var sealed = repairs.sealManifest(context.lease(), repairs.load(task.taskId()), baselines);
    if (sealed.kind() != RepairWriteKind.APPLIED && sealed.kind() != RepairWriteKind.REPLAY)
      throw new IOException("清单封存尚未确认");
    return new CompletedDataset(
        task.taskId(),
        snapshot.generation(),
        config,
        snapshot.blocks(),
        snapshot.verifiedPrefixCount());
  }

  /** 只重读不可变分段区间；源内容、数量和边界都一致才写新块。 */
  public VerifiedBlock rebuildSegment(
      GenerationContext context,
      SegmentRef segment,
      AttemptWorkspace workspace,
      ExecutionGuard guard)
      throws IOException {
    check(guard);
    var rows =
        orders.readSegment(
            context.lease().taskId(),
            context.snapshot().scope(),
            segment.previousCursor(),
            segment.lastCursor(),
            segment.rowCount() + 1);
    if (rows.size() != segment.rowCount()
        || !codec.checksumRows(rows, segment.formatVersion()).equals(segment.contentChecksum()))
      throw new InvalidExportDataException("DATA_REPAIR_SOURCE_CHANGED");
    var last = rows.getLast();
    if (!new OrderCursor(last.createdAt(), last.id()).equals(segment.lastCursor()))
      throw new InvalidExportDataException("DATA_REPAIR_SOURCE_CHANGED");
    check(guard);
    var header =
        new BlockHeader(
            segment.formatVersion(),
            segment.configurationHash(),
            repairs.load(context.lease().taskId()).dataGeneration(),
            segment.sequence(),
            segment.previousCursor(),
            segment.lastCursor(),
            segment.rowCount());
    var ref = storage.writeBlock(workspace, header, rows);
    check(guard);
    return new VerifiedBlock(ref, segment.contentChecksum());
  }

  public TrustedPrefix validatePrefix(CheckpointSnapshot snapshot, String config)
      throws IOException {
    return validatePrefix(snapshot, config, () -> true);
  }

  private TrustedPrefix validatePrefix(
      CheckpointSnapshot snapshot, String config, ExecutionGuard guard) throws IOException {
    check(guard);
    if (!snapshot.configurationHash().equals(config)) throw new InvalidExportDataException();
    int sequence = 0;
    long count = 0;
    OrderCursor last = null;
    for (var block : snapshot.blocks()) {
      check(guard);
      var header = block.header();
      if (header.sequence() != ++sequence
          || header.version() != 1
          || header.generation() != snapshot.generation()
          || !header.configHash().equals(config)
          || !Objects.equals(last, header.previousCursor())) throw new InvalidExportDataException();
      int rows = 0;
      OrderCursor cursor = last;
      try (var reader = storage.readBlock(block)) {
        for (var next = reader.next(); next.isPresent(); next = reader.next()) {
          check(guard);
          var row = next.get();
          var actual = new OrderCursor(row.createdAt(), row.id());
          if (cursor != null
              && (actual.createdAt().isAfter(cursor.createdAt())
                  || actual.createdAt().equals(cursor.createdAt()) && actual.id() >= cursor.id()))
            throw new InvalidExportDataException();
          cursor = actual;
          rows++;
        }
      }
      if (rows != header.rowCount() || !Objects.equals(cursor, header.lastCursor()))
        throw new InvalidExportDataException();
      last = cursor;
      count += rows;
    }
    if (count != snapshot.verifiedPrefixCount() || !Objects.equals(last, snapshot.cursor()))
      throw new InvalidExportDataException();
    return new TrustedPrefix(
        snapshot.generation(), snapshot.version(), snapshot.blocks(), last, count, true);
  }

  private void check(ExecutionGuard guard) throws IOException {
    if (!guard.check() || Thread.currentThread().isInterrupted())
      throw new ExecutionStoppedException();
  }
}
