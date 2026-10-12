// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.orders;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// [SC-001](../../../../../../../docs/product/acceptance-criteria.md#sc-001)
// [SC-002](../../../../../../../docs/product/acceptance-criteria.md#sc-002)
// [AC-011](../../../../../../../docs/product/acceptance-criteria.md#ac-011)
@EnabledIfSystemProperty(named = "exportflow.performance", matches = "true")
class ApiPerformanceTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @FunctionalInterface
  private interface Request {
    java.net.http.HttpResponse<String> send() throws Exception;
  }

  @Test
  void TC227_TC228_referenceApiSamplesKeepRawLatenciesAndNearestRankP95() throws Exception {
    useOrderVolume(110000);
    var cases = new LinkedHashMap<String, Request>();
    cases.put("default", () -> get("/api/v1/orders"));
    cases.put("status-channel", () -> get("/api/v1/orders?status=PAID&channel=WEB"));
    cases.put(
        "amount-date",
        () -> get("/api/v1/orders?minAmount=0&maxAmount=9999&createdFrom=2026-01-01T00:00:00Z"));
    cases.put("literal-keyword", () -> get("/api/v1/orders?keyword=客户"));
    cases.put("create-filtered", () -> post(filtered(), newKey()));
    List<String> ids =
        db.queryForList("SELECT CAST(id AS CHAR) FROM orders ORDER BY id", String.class);
    String selected =
        json.writeValueAsString(
            Map.of(
                "scope",
                Map.of("type", "SELECTED", "orderIds", ids),
                "fields",
                List.of("orderNo")));
    cases.put("create-large-selected", () -> post(selected, newKey()));
    List<Map<String, Object>> records = new ArrayList<>();
    Path output =
        Path.of(
                System.getProperty(
                    "exportflow.evidenceRoot", "../../artifacts/002-order-management"))
            .resolve("performance-api.json")
            .toAbsolutePath()
            .normalize();
    Files.createDirectories(output.getParent());
    try {
      for (var entry : cases.entrySet()) measure(entry.getKey(), entry.getValue(), records);
      rabbit.suspendConnections();
      try {
        measure("create-broker-unavailable", () -> post(filtered(), newKey()), records);
      } finally {
        rabbit.resumeConnections();
      }
    } finally {
      json.writerWithDefaultPrettyPrinter()
          .writeValue(
              output.toFile(),
              Map.of(
                  "source",
                  "exportflow.orders",
                  "rows",
                  110000,
                  "warmup",
                  30,
                  "samples",
                  200,
                  "scenarios",
                  records,
                  "environment",
                  Map.of(
                      "time",
                      Instant.now().toString(),
                      "java",
                      System.getProperty("java.runtime.version"),
                      "os",
                      System.getProperty("os.name"),
                      "maxHeapBytes",
                      Runtime.getRuntime().maxMemory(),
                      "mysqlImage",
                      mysql.getDockerImageName(),
                      "rabbitImage",
                      rabbit.getDockerImageName())));
    }
  }

  private void measure(String name, Request request, List<Map<String, Object>> records)
      throws Exception {
    for (int i = 0; i < 30; i++) assertThat(request.send().statusCode()).isIn(200, 201);
    List<Double> times = new ArrayList<>();
    List<Integer> statuses = new ArrayList<>();
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("name", name);
    record.put("milliseconds", times);
    record.put("statusCodes", statuses);
    records.add(record);
    for (int i = 0; i < 200; i++) {
      long started = System.nanoTime();
      var response = request.send();
      times.add((System.nanoTime() - started) / 1_000_000.0);
      statuses.add(response.statusCode());
      assertThat(response.statusCode()).isIn(200, 201);
    }
    List<Double> sorted = new ArrayList<>(times);
    sorted.sort(Double::compareTo);
    double p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
    record.put("p95Milliseconds", p95);
    assertThat(p95).isLessThanOrEqualTo(500.0);
  }
}
