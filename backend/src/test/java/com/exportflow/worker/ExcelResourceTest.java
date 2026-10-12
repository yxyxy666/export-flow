// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.time.Duration;
import org.junit.jupiter.api.Test;

// [SC-004](../../../../../../../docs/product/acceptance-criteria.md#sc-004)
// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
class ExcelResourceTest extends ProductionBackendFixture {
  @Test
  void TC231_real110000NineColumnExportRunsInIndependent512MiBWorkerWithoutOom() throws Exception {
    useOrderVolume(110000);
    var task = createVolume();
    call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    var worker =
        startWorker(java.util.List.of("--test-heap-report=" + root.resolve("worker-heap.json")));
    try {
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofMinutes(5))
          .untilAsserted(
              () -> {
                assertThat(worker.isAlive()).as("512MB子进程在生成期间不能OOM退出").isTrue();
                assertThat(state(task)).isEqualTo("SUCCEEDED");
              });
      assertThat(
              db.queryForObject(
                  "SELECT total_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(110000);
      assertThat(
              db.queryForObject(
                  "SELECT processed_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(110000);
      var sample = json.readTree(root.resolve("worker-heap.json").toFile());
      assertThat(sample.get("maxHeapBytes").asLong()).isLessThanOrEqualTo(512L * 1024 * 1024);
      assertThat(sample.get("peakSampleBytes").asLong())
          .isPositive()
          .isLessThanOrEqualTo(512L * 1024 * 1024);
      assertThat(sample.get("sampleCount").asLong()).isGreaterThan(1);
      try (var files = java.nio.file.Files.walk(root.resolve("scratch"))) {
        assertThat(files.filter(java.nio.file.Files::isRegularFile).toList()).isEmpty();
      }
    } finally {
      stopWorker(worker);
    }
    assertThat(java.nio.file.Files.readString(root.resolve("worker-barrier.log")))
        .doesNotContain("OutOfMemoryError");
    // 独立进程真实堆采样与scratch检查；五次时间度量由ExcelPerformanceTest单独执行。
  }
}
