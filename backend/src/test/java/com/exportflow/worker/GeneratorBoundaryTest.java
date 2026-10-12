// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
class GeneratorBoundaryTest extends ProductionBackendFixture {
  @Test
  void TC225_realGeneratorBeanIsUniqueAvailableAndHasFileRoot() throws Exception {
    assertThat(
            call(
                "com.exportflow.worker.infrastructure.excel.PoiExportFileGenerator", "isAvailable"))
        .isEqualTo(true);
    assertThat(app.getBeansOfType(Class.forName("com.exportflow.worker.port.ExportFileGenerator")))
        .hasSize(1);
    assertThat(java.nio.file.Files.isDirectory(root)).isTrue();
  }
}
