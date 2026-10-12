// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.worker.domain.ExecutionModels.ExecutionLease;
import com.exportflow.worker.infrastructure.persistence.CheckpointRecord.BlockRecord;
import com.exportflow.worker.port.CheckpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 块登记是进度唯一写入口，旧fence/前序版本拒绝；设计 function-design-reference.md，TC-235。 */
@Repository
public class MyBatisCheckpointRepository implements CheckpointRepository {
  private final CheckpointMapper checkpoints;
  private final TaskExecutionMapper executions;
  private final ExportTaskRepository tasks;
  private final CommandOutboxMapper clock;
  private final ObjectMapper json;
  private final TransactionTemplate transaction;

  public MyBatisCheckpointRepository(
      CheckpointMapper checkpoints,
      TaskExecutionMapper executions,
      ExportTaskRepository tasks,
      CommandOutboxMapper clock,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.checkpoints = checkpoints;
    this.executions = executions;
    this.tasks = tasks;
    this.clock = clock;
    this.json = json;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public CheckpointSnapshot find(UUID taskId) {
    var row = checkpoints.find(taskId.toString());
    if (row == null)
      return new CheckpointSnapshot(
          taskId,
          1,
          0,
          null,
          0,
          0,
          tasks.findSnapshot(taskId).orElseThrow().configurationHash(),
          List.of());
    var blocks =
        checkpoints.findBlocks(taskId.toString(), row.dataGeneration()).stream()
            .map(this::block)
            .toList();
    return new CheckpointSnapshot(
        taskId,
        row.dataGeneration(),
        row.checkpointVersion(),
        row.cursorCreatedAt() == null
            ? null
            : new OrderCursor(row.cursorCreatedAt().toInstant(ZoneOffset.UTC), row.cursorOrderId()),
        row.verifiedPrefixCount(),
        row.processedHighWater(),
        row.configurationHash(),
        blocks);
  }

  public CheckpointWriteResult commitBlock(
      ExecutionLease lease, CheckpointSnapshot expected, BlockRef ref) {
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(lease.taskId());
          var execution = executions.lock(lease.taskId().toString());
          var row = checkpoints.lock(lease.taskId().toString());
          Instant now = clock.databaseNow();
          if (task == null
              || task.status() != TaskStatus.RUNNING
              || execution == null
              || !execution.accepts(lease, now)) return stale();
          if (row == null) {
            if (expected.version() != 0 || expected.generation() != 1) return stale();
            checkpoints.initialize(lease.taskId().toString(), expected.configurationHash());
            row = checkpoints.lock(lease.taskId().toString());
          }
          var h = ref.header();
          var persistedCursor =
              row.cursorCreatedAt() == null
                  ? null
                  : new OrderCursor(
                      row.cursorCreatedAt().toInstant(ZoneOffset.UTC), row.cursorOrderId());
          long next = expected.verifiedPrefixCount() + h.rowCount();
          if (!expected.taskId().equals(lease.taskId())
              || row.checkpointVersion() != expected.version()
              || row.dataGeneration() != expected.generation()
              || row.verifiedPrefixCount() != expected.verifiedPrefixCount()
              || !Objects.equals(persistedCursor, expected.cursor())
              || !row.configurationHash().equals(expected.configurationHash())
              || h.generation() != row.dataGeneration()
              || !row.configurationHash().equals(h.configHash())
              || h.version() != 1
              || h.sequence() != expected.blocks().size() + 1
              || !Objects.equals(expected.cursor(), h.previousCursor())
              || h.lastCursor() == null
              || next > task.totalCount()
              || h.rowCount() < 1
              || h.rowCount() > 1000
              || !ref.storageRef().taskId().equals(lease.taskId())
              || !"BLOCK".equals(ref.storageRef().kind())
              || !Objects.equals(ref.storageRef().generation(), h.generation())
              || !ref.blockId().equals(ref.storageRef().identity())
              || ref.storageRef().attemptFence() != null
              || ref.storageRef().owner() != null
              || ref.byteSize() <= 0
              || !ref.checksum().matches("[0-9a-f]{64}")) return stale();
          checkpoints.insertBlock(record(ref, lease));
          checkpoints.updateCheckpoint(
              lease.taskId().toString(), next, h.lastCursor().createdAt(), h.lastCursor().id());
          executions.advanceProgressVersion(lease.taskId().toString());
          executions.updateProgress(lease.taskId().toString(), next);
          return new CheckpointWriteResult(CheckpointWriteKind.APPLIED, find(lease.taskId()));
        });
  }

  public Optional<BlockRef> reconcileBlock(UUID taskId, UUID blockId) {
    return checkpoints.findBlock(taskId.toString(), blockId.toString()).map(this::block);
  }

  public CheckpointWriteResult startGeneration(
      ExecutionLease lease, long expectedVersion, TrustedPrefix prefix) {
    if (!prefix.isSafeToRedo()) return stale();
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(lease.taskId());
          var execution = executions.lock(lease.taskId().toString());
          var row = checkpoints.lock(lease.taskId().toString());
          var now = clock.databaseNow();
          if (task == null
              || task.status() != TaskStatus.RUNNING
              || execution == null
              || !execution.accepts(lease, now)
              || row == null
              || row.checkpointVersion() != expectedVersion
              || prefix.version() != expectedVersion
              || prefix.generation() != row.dataGeneration()) return stale();
          checkpoints.startGeneration(lease.taskId().toString());
          return new CheckpointWriteResult(CheckpointWriteKind.APPLIED, find(lease.taskId()));
        });
  }

  private CheckpointWriteResult stale() {
    return new CheckpointWriteResult(CheckpointWriteKind.STALE, null);
  }

  private BlockRef block(BlockRecord row) {
    try {
      return new BlockRef(
          UUID.fromString(row.blockId()),
          new BlockHeader(
              row.formatVersion(),
              row.configurationHash(),
              row.dataGeneration(),
              row.sequence(),
              row.previousCursorJson() == null
                  ? null
                  : json.readValue(row.previousCursorJson(), OrderCursor.class),
              json.readValue(row.lastCursorJson(), OrderCursor.class),
              row.rowCount()),
          json.readValue(row.storageRef(), StorageRef.class),
          row.byteSize(),
          row.checksum());
    } catch (java.io.IOException malformed) {
      throw new InvalidExportDataException();
    }
  }

  private BlockRecord record(BlockRef ref, ExecutionLease lease) {
    try {
      var h = ref.header();
      return new BlockRecord(
          ref.blockId().toString(),
          lease.taskId().toString(),
          h.generation(),
          h.sequence(),
          h.previousCursor() == null ? null : json.writeValueAsString(h.previousCursor()),
          json.writeValueAsString(h.lastCursor()),
          h.rowCount(),
          ref.byteSize(),
          ref.checksum(),
          json.writeValueAsString(ref.storageRef()),
          h.version(),
          h.configHash(),
          lease.owner(),
          lease.fence());
    } catch (java.io.IOException impossible) {
      throw new InvalidExportDataException();
    }
  }
}
