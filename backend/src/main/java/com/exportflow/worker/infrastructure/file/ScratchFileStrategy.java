// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.infrastructure.file;

import com.exportflow.exporttasks.domain.ExportFileModels.AttemptWorkspace;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.apache.poi.util.DefaultTempFileCreationStrategy;

/** 进程启动只安装一次策略；线程作用域让POI临时文件携带可裁决的task/fence身份。 */
public final class ScratchFileStrategy extends DefaultTempFileCreationStrategy {
  private static final ThreadLocal<AttemptWorkspace> CURRENT = new ThreadLocal<>();
  private final Path processRoot;

  public ScratchFileStrategy(Path processRoot) {
    super(processRoot.toFile());
    this.processRoot = processRoot;
  }

  public static Scope use(AttemptWorkspace workspace) {
    var prior = CURRENT.get();
    CURRENT.set(workspace);
    return new Scope(prior);
  }

  public static final class Scope implements AutoCloseable {
    private final AttemptWorkspace prior;

    private Scope(AttemptWorkspace prior) {
      this.prior = prior;
    }

    @Override
    public void close() {
      if (prior == null) CURRENT.remove();
      else CURRENT.set(prior);
    }
  }

  @Override
  public File createTempFile(String prefix, String suffix) throws IOException {
    var workspace = CURRENT.get();
    if (workspace == null) return super.createTempFile(prefix, suffix);
    Path directory =
        processRoot
            .resolve(workspace.taskId().toString())
            .resolve(workspace.fence() + "-" + workspace.owner());
    com.exportflow.exporttasks.infrastructure.file.LocalExportFileStorage.checkPath(directory);
    Files.createDirectories(directory);
    com.exportflow.exporttasks.infrastructure.file.LocalExportFileStorage.checkPath(directory);
    Path file = directory.resolve(UUID.randomUUID() + ".tmp");
    return Files.createFile(file).toFile();
  }
}
