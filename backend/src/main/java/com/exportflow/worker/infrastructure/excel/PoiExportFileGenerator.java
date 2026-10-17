// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[GeneratorBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/GeneratorBoundaryTest.java)、[WorkerFailureWindowsTest.java](../../../../../../../test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)。
package com.exportflow.worker.infrastructure.excel;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.*;
import com.exportflow.worker.application.AssemblyRepairService;
import com.exportflow.worker.application.ExportDataCollectionService;
import com.exportflow.worker.domain.AssemblyRepairModels.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.port.*;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 先对账READY，成功证据需校验及登记；设计 contracts/worker-boundary.md，TC-225/235。 */
@Component
public class PoiExportFileGenerator implements ExportFileGenerator {
  private final ExportFileStorage storage;
  private final ArtifactRepository artifacts;
  private final ExportDataCollectionService collection;
  private final ExcelWorkbookAssembler assembler;
  private final ExcelFileValidator validator;
  private final boolean available;
  private final com.exportflow.worker.application.TaskExecutionService executions;
  private final AssemblyRepairRepository repairs;
  private final AssemblyRepairService repairService;

  public PoiExportFileGenerator(
      ExportFileStorage storage,
      ArtifactRepository artifacts,
      ExportDataCollectionService collection,
      ExcelWorkbookAssembler assembler,
      ExcelFileValidator validator,
      com.exportflow.worker.application.TaskExecutionService executions,
      AssemblyRepairRepository repairs,
      AssemblyRepairService repairService) {
    this.storage = storage;
    this.artifacts = artifacts;
    this.collection = collection;
    this.assembler = assembler;
    this.validator = validator;
    this.executions = executions;
    this.repairs = repairs;
    this.repairService = repairService;
    available = storage.isAvailable();
  }

  public boolean isAvailable() {
    return available;
  }

  public GenerationResult generate(GenerationContext context, ExecutionGuard guard) {
    if (!available) return unknown();
    if (!guard.check()) return stopped(context, guard);
    try {
      var workspace = storage.prepareAttempt(context);
      collection.collect(context, guard);
      Optional<ArtifactProof> existing =
          artifacts.findReady(context.lease().taskId(), context.snapshot().configurationHash());
      RepairIssue trigger = null;
      while (true) {
        if (!guard.check()) return stopped(context, guard);
        if (repairs.markChecking(context.lease()).kind() != RepairWriteKind.APPLIED)
          return unknown();
        var manifest = repairs.load(context.lease().taskId());
        var audit = repairService.audit(context, manifest, trigger, guard);
        if (audit.fatalReason() != null) throw new InvalidExportDataException(audit.fatalReason());
        if (!audit.damagedSegments().isEmpty() || trigger != null) {
          repairService.rebuild(context, audit, workspace, guard);
          trigger = null;
          continue;
        }
        if (repairs.markAssembling(context.lease(), audit).kind() != RepairWriteKind.APPLIED)
          return unknown();
        var data =
            new CompletedDataset(
                manifest.taskId(),
                manifest.dataGeneration(),
                manifest.configurationHash(),
                manifest.segments().stream().map(SegmentRef::selectedBlock).toList(),
                manifest.totalCount());
        StagingWorkbook staging;
        ValidatedWorkbook checked;
        boolean isWorkbook = false;
        try {
          if (existing.isPresent()) {
            var proof = existing.get();
            isWorkbook = true;
            checked =
                validator.validate(
                    storage.resolve(proof.artifactRef()),
                    proof.artifactRef(),
                    context.snapshot(),
                    guard);
            if (checked.size() != proof.size()
                || !checked.checksum().equals(proof.checksum())
                || checked.rowCount() != proof.rowCount()
                || !checked.configHash().equals(proof.configurationHash())
                || !checked.ref().equals(proof.artifactRef())
                || proof.dataGeneration() != manifest.dataGeneration())
              throw new InvalidExportDataException("EXPORT_FILE_INVALID");
            return new GenerationResult(GenerationKind.READY, proof, null, null, true);
          }
          staging = assembler.assemble(context, data, workspace, guard);
          isWorkbook = true;
          checked =
              validator.validate(
                  storage.resolve(staging.ref()), staging.ref(), context.snapshot(), guard);
        } catch (InvalidExportDataException damaged) {
          if (damaged.getSuppressed().length > 0) return unknown();
          trigger = new RepairIssue(isWorkbook ? "WORKBOOK_CORRUPT" : "DATA_BLOCK_CORRUPT", null);
          existing = Optional.empty();
          continue;
        } catch (java.nio.file.NoSuchFileException | java.io.FileNotFoundException missing) {
          if (missing.getSuppressed().length > 0) return unknown();
          trigger = new RepairIssue(isWorkbook ? "WORKBOOK_CORRUPT" : "DATA_BLOCK_MISSING", null);
          existing = Optional.empty();
          continue;
        }
        if (!guard.check()) return stopped(context, guard);
        var proof = storage.publish(workspace, staging, checked);
        if (!guard.check()) return stopped(context, guard);
        if (artifacts.registerReady(context.lease(), proof) != LeaseWriteResult.APPLIED)
          return unknown();
        return new GenerationResult(GenerationKind.READY, proof, null, null, true);
      }
    } catch (InvalidExportDataException invalid) {
      if (invalid.getSuppressed().length > 0) return unknown();
      return new GenerationResult(
          GenerationKind.KNOWN_FAILURE,
          null,
          new SafeFailure(
              invalid.code(),
              invalid.code().startsWith("DATA_REPAIR_") ? "文件重建失败，请联系处理" : "导出数据校验失败，请核对订单范围与数据"),
          null,
          true);
    } catch (ExecutionStoppedException stopped) {
      return stopped.getSuppressed().length == 0 ? stopped(context, guard) : unknown();
    } catch (java.nio.file.AccessDeniedException inaccessible) {
      return new GenerationResult(
          GenerationKind.KNOWN_FAILURE,
          null,
          new SafeFailure("DATA_REPAIR_IO_ERROR", "文件访问失败，请联系处理"),
          null,
          false);
    } catch (IOException | RuntimeException unavailable) {
      return unknown();
    }
  }

  private GenerationResult unknown() {
    return new GenerationResult(GenerationKind.UNKNOWN, null, null);
  }

  private GenerationResult stopped(GenerationContext context, ExecutionGuard guard) {
    if (guard.stopReason() != StopReason.USER_PAUSE)
      return new GenerationResult(GenerationKind.UNKNOWN, null, null, null, true);
    var control = executions.findControl(context.lease().taskId());
    if (control == null || !"KNOWN".equals(control.readOutcome())) return unknown();
    var lease = context.lease();
    return new GenerationResult(
        GenerationKind.PAUSE_STOPPED,
        null,
        null,
        new PauseProof(
            lease.taskId(),
            lease.owner(),
            lease.fence(),
            control.controlVersion(),
            control.checkpointVersion()),
        true);
  }
}
