// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[DownloadBoundaryTest.java](../../../../../../test/java/com/exportflow/worker/DownloadBoundaryTest.java)、[DownloadExportFileTest.java](../../../../../../test/java/com/exportflow/worker/DownloadExportFileTest.java)。
package com.exportflow.exporttasks.application;

import com.exportflow.exporttasks.domain.ExportFileModels.DownloadHandle;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.*;
import com.exportflow.shared.domain.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 短读取后打开流，下载不持数据库事务；设计 excel-design.md，TC-234。 */
@Service
public class DownloadExportFileService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DownloadExportFileService.class);
  private final ExportTaskRepository tasks;
  private final ArtifactRepository artifacts;
  private final ExportFileStorage storage;

  public DownloadExportFileService(
      ExportTaskRepository tasks, ArtifactRepository artifacts, ExportFileStorage storage) {
    this.tasks = tasks;
    this.artifacts = artifacts;
    this.storage = storage;
  }

  public DownloadHandle prepare(UUID taskId) {
    try {
      var snapshot =
          tasks
              .findSnapshot(taskId)
              .orElseThrow(() -> new BusinessException("TASK_NOT_FOUND", "任务不存在", 404));
      if (snapshot.task().status() != TaskStatus.SUCCEEDED)
        throw new BusinessException("TASK_NOT_DOWNLOADABLE", "导出文件尚未就绪", 409);
      var proof =
          artifacts
              .findAdopted(taskId)
              .orElseThrow(() -> new BusinessException("EXPORT_FILE_INVALID", "导出文件引用异常", 500));
      if (!proof.artifactId().equals(snapshot.task().resultArtifactId())
          || proof.rowCount() != snapshot.task().totalCount()
          || proof.size() != snapshot.task().fileSize()
          || !proof.fileName().equals(snapshot.task().fileName())
          || !proof.fileName().equals(snapshot.fileName())
          || !proof.configurationHash().equals(snapshot.configurationHash())
          || !proof.taskId().equals(taskId)
          || !proof.artifactId().equals(proof.artifactRef().identity())
          || !proof.taskId().equals(proof.artifactRef().taskId())
          || !"ARTIFACT".equals(proof.artifactRef().kind()))
        throw new BusinessException("EXPORT_FILE_INVALID", "导出文件引用异常", 500);
      return storage.openDownload(proof);
    } catch (BusinessException known) {
      throw known;
    } catch (java.nio.file.NoSuchFileException | java.io.FileNotFoundException missing) {
      LOG.warn("export file missing taskId={}", taskId);
      throw new BusinessException("EXPORT_FILE_MISSING", "导出文件缺失，请联系维护人员", 410);
    } catch (java.io.IOException | IllegalArgumentException invalid) {
      throw new BusinessException("EXPORT_FILE_UNAVAILABLE", "暂时无法读取导出文件", 500);
    } catch (RuntimeException unavailable) {
      throw new BusinessException("DEPENDENCY_UNAVAILABLE", "暂时无法查询文件，请重试", 503);
    }
  }
}
