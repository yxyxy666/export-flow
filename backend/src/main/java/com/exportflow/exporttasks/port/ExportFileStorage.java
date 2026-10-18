// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[DownloadBoundaryTest.java](../../../../../../test/java/com/exportflow/worker/DownloadBoundaryTest.java)、[DownloadExportFileTest.java](../../../../../../test/java/com/exportflow/worker/DownloadExportFileTest.java)。
package com.exportflow.exporttasks.port;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.orders.domain.Order;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** 文件存在不代表就绪或成功；设计 function-design-reference.md，TC-234/235。 */
public interface ExportFileStorage {
  boolean isAvailable();

  AttemptWorkspace prepareAttempt(GenerationContext context) throws IOException;

  BlockRef writeBlock(AttemptWorkspace workspace, BlockHeader header, List<Order> rows)
      throws IOException;

  BlockReader readBlock(BlockRef ref) throws IOException;

  ArtifactProof publish(
      AttemptWorkspace workspace, StagingWorkbook staging, ValidatedWorkbook proof)
      throws IOException;

  Path resolve(StorageRef ref) throws IOException;

  DownloadHandle openDownload(ArtifactProof proof) throws IOException;

  void closeWorkspace(AttemptWorkspace workspace) throws IOException;

  /** 删除任务专属目录；调用方必须先完成数据库引用保护裁决。 */
  default void deleteTaskFiles(UUID taskId) throws IOException {}
}
