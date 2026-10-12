// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[GeneratorBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/GeneratorBoundaryTest.java)、[WorkerFailureWindowsTest.java](../../../../../../../test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)。
package com.exportflow.worker.infrastructure.excel;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.*;
import com.exportflow.worker.application.ExportDataCollectionService;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.port.*;
import java.io.IOException;
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

  public PoiExportFileGenerator(
      ExportFileStorage storage,
      ArtifactRepository artifacts,
      ExportDataCollectionService collection,
      ExcelWorkbookAssembler assembler,
      ExcelFileValidator validator) {
    this.storage = storage;
    this.artifacts = artifacts;
    this.collection = collection;
    this.assembler = assembler;
    this.validator = validator;
    available = storage.isAvailable();
  }

  public boolean isAvailable() {
    return available;
  }

  public GenerationResult generate(GenerationContext context, ExecutionGuard guard) {
    if (!available || !guard.check()) return unknown();
    try {
      var existing =
          artifacts.findReady(context.lease().taskId(), context.snapshot().configurationHash());
      if (existing.isPresent()) {
        var proof = existing.get();
        var checked =
            validator.validate(
                storage.resolve(proof.artifactRef()),
                proof.artifactRef(),
                context.snapshot(),
                guard);
        if (checked.size() != proof.size()
            || !checked.checksum().equals(proof.checksum())
            || checked.rowCount() != proof.rowCount()
            || !checked.configHash().equals(proof.configurationHash())
            || !checked.ref().equals(proof.artifactRef()))
          throw new InvalidExportDataException("EXPORT_FILE_INVALID");
        return new GenerationResult(GenerationKind.READY, proof, null);
      }
      var workspace = storage.prepareAttempt(context);
      var data = collection.collect(context, guard);
      var staging = assembler.assemble(context, data, workspace, guard);
      var checked =
          validator.validate(
              storage.resolve(staging.ref()), staging.ref(), context.snapshot(), guard);
      if (!guard.check()) return unknown();
      var proof = storage.publish(workspace, staging, checked);
      if (!guard.check()) return unknown();
      var registered = artifacts.registerReady(context.lease(), proof);
      if (registered != LeaseWriteResult.APPLIED) return unknown();
      return new GenerationResult(GenerationKind.READY, proof, null);
    } catch (InvalidExportDataException invalid) {
      return new GenerationResult(
          GenerationKind.KNOWN_FAILURE,
          null,
          new SafeFailure(invalid.code(), "导出数据校验失败，请核对订单范围与数据"));
    } catch (IOException | RuntimeException unavailable) {
      return unknown();
    }
  }

  private GenerationResult unknown() {
    return new GenerationResult(GenerationKind.UNKNOWN, null, null);
  }
}
