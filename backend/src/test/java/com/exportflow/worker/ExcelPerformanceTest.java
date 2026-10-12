// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [SC-003](../../../../../../../docs/product/acceptance-criteria.md#sc-003)
// [SC-004](../../../../../../../docs/product/acceptance-criteria.md#sc-004)
@EnabledIfSystemProperty(named = "exportflow.performance", matches = "true")
class ExcelPerformanceTest extends BoundaryFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @ParameterizedTest
  @ValueSource(ints = {110000, 200000})
  void TC230_TC231_fiveSerialFullPipelineRunsWithHeapAndScratchSamples(int count) throws Exception {
    assertThat(Runtime.getRuntime().maxMemory()).isLessThanOrEqualTo(512L * 1024 * 1024);
    useOrderVolume(count);
    List<Map<String, Object>> trials = new ArrayList<>();
    Path report =
        Path.of(
                System.getProperty(
                    "exportflow.evidenceRoot", "../../artifacts/002-order-management"))
            .resolve("performance-excel-" + count + ".json")
            .toAbsolutePath()
            .normalize();
    Files.createDirectories(report.getParent());
    try {
      for (int trial = 1; trial <= 5; trial++) {
        var started = new AtomicLong();
        var finished = new AtomicLong();
        var wall = new AtomicReference<Instant>();
        var peak = new AtomicLong();
        var samples = new AtomicLong();
        probe.on(
            GENERATOR,
            "generate",
            (m, a, real) -> {
              started.compareAndSet(0, System.nanoTime());
              wall.compareAndSet(null, Instant.now());
              return real.run();
            });
        probe.on(
            EXECUTION,
            "complete",
            (m, a, real) -> {
              Object result = real.run();
              if (result.toString().equals("APPLIED")) finished.set(System.nanoTime());
              return result;
            });
        java.util.UUID task;
        try (var sampler = Executors.newSingleThreadScheduledExecutor()) {
          sampler.scheduleAtFixedRate(
              () -> {
                peak.accumulateAndGet(
                    ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), Math::max);
                samples.incrementAndGet();
              },
              0,
              50,
              TimeUnit.MILLISECONDS);
          task = createVolume();
          org.awaitility.Awaitility.await()
              .pollInterval(Duration.ofMillis(20))
              .atMost(Duration.ofMinutes(3))
              .untilAsserted(() -> assertThat(state(task)).isEqualTo("SUCCEEDED"));
          assertThat(started.get()).isPositive();
          sampler.shutdown();
          assertThat(sampler.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(finished.get()).isGreaterThan(started.get());
        double elapsed = (finished.get() - started.get()) / 1_000_000.0;
        var accepted =
            db.queryForObject(
                    "SELECT created_at FROM export_tasks WHERE task_id=?",
                    java.sql.Timestamp.class,
                    task.toString())
                .toLocalDateTime()
                .toInstant(ZoneOffset.UTC);
        long queueWaitMs = Duration.between(accepted, wall.get()).toMillis();
        try (var files = Files.walk(root.resolve("scratch"))) {
          assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
        byte[] file = download(task); // 下载/测试打开校验不计入生成耗时。
        Path opened = root.resolve("performance-" + trial + ".xlsx");
        Files.write(opened, file);
        try (var pkg =
            org.apache.poi.openxml4j.opc.OPCPackage.open(
                opened.toFile(), org.apache.poi.openxml4j.opc.PackageAccess.READ)) {
          var reader = new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
          assertThat(reader.getSheetsData().hasNext()).isTrue();
        }
        trials.add(
            Map.of(
                "trial",
                trial,
                "taskId",
                task.toString(),
                "milliseconds",
                elapsed,
                "queueWaitMilliseconds",
                queueWaitMs,
                "heapPeakSampleBytes",
                peak.get(),
                "heapSamples",
                samples.get(),
                "fileBytes",
                file.length));
        assertThat(samples.get()).isPositive();
        assertThat(elapsed).isLessThanOrEqualTo(count == 110000 ? 60000.0 : 120000.0);
        // 测量探针仅委托真实算法；清理Mockito保存的整批订单参数，避免跨次累积测试堆。
        org.mockito.Mockito.clearInvocations(
            app.getBean(com.exportflow.worker.infrastructure.file.DataBlockCodec.class),
            app.getBean(com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler.class),
            app.getBean(com.exportflow.worker.infrastructure.excel.ExcelFileValidator.class));
      }
    } finally {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("source", "exportflow.orders");
      data.put("rows", count);
      data.put("columns", 9);
      data.put("trials", trials);
      data.put("java", System.getProperty("java.runtime.version"));
      data.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
      data.put("processors", Runtime.getRuntime().availableProcessors());
      data.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
      data.put("time", Instant.now().toString());
      data.put("mysqlImage", mysql.getDockerImageName());
      data.put("rabbitImage", rabbit.getDockerImageName());
      json.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), data);
    }
    assertThat(trials).hasSize(5);
  }
}
