// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

// [AC-010](../../../../../../../docs/product/acceptance-criteria.md#ac-010)
// [AC-064](../../../../../../../docs/product/acceptance-criteria.md#ac-064)
class IdempotencyMySqlTest extends ProductionBackendFixture {
  private final com.exportflow.testing.CommitFaults exclusionFaults =
      new com.exportflow.testing.CommitFaults();

  @Override
  protected org.springframework.beans.factory.config.BeanPostProcessor testFaults() {
    return exclusionFaults;
  }

  // 增量：[任务与证据](../../../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
  // [AC-079](../../../../../../../docs/product/acceptance-criteria.md#ac-079)
  // [AC-051](../../../../../../../docs/product/acceptance-criteria.md#ac-051)
  @Test
  void TC313_legacyCanonicalBytesAndEmptyExclusionReplayRemainVersionOne() throws Exception {
    String key = newKey();
    var original = post(filtered(), key);
    assertThat(original.statusCode()).isEqualTo(201);
    String task = body(original).get("taskId").asText();
    // 固定旧三字段序列化字节，不使用新ExportScope自动序列化作为oracle。
    String legacy =
        "{\"scope\":{\"type\":\"FILTERED\",\"orderIds\":null,\"filter\":{\"keyword\":null,\"orderNo\":null,\"status\":null,\"channel\":null,\"createdFrom\":null,\"createdTo\":null,\"minAmount\":null,\"maxAmount\":null}},\"fields\":[\"orderNo\",\"amount\",\"createdAt\"]}";
    byte[] expected =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(
            db.queryForObject(
                "SELECT request_hash FROM export_idempotency WHERE task_id=?", byte[].class, task))
        .isEqualTo(expected);
    assertThat(
            db.queryForObject(
                "SELECT canonical_version FROM export_idempotency WHERE task_id=?",
                Integer.class,
                task))
        .isEqualTo(1);
    var replay =
        post(filtered().replace("\"filter\":{}", "\"filter\":{},\"excludedOrderIds\":[]"), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(original));
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @Test
  void TC313_exclusionOrderingAndDuplicatesReplayButChangedSetConflicts() throws Exception {
    String key = newKey();
    String request =
        filtered()
            .replace("\"filter\":{}", "\"filter\":{},\"excludedOrderIds\":[\"2\",\"1\",\"1\"]");
    var first = post(request, key);
    assertThat(first.statusCode()).isEqualTo(201);
    String task = body(first).get("taskId").asText();
    assertThat(
            db.queryForObject(
                "SELECT canonical_version FROM export_idempotency WHERE task_id=?",
                Integer.class,
                task))
        .isEqualTo(2);
    var replay = post(request.replace("[\"2\",\"1\",\"1\"]", "[\"1\",\"2\"]"), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(first));
    var conflict = post(request.replace("[\"2\",\"1\",\"1\"]", "[\"1\"]"), key);
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(body(conflict).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(count("export_task_orders")).isEqualTo(2);
  }

  @Test
  void TC313_unknownCommitReconciliationRetainsOriginalExclusionAndKey() throws Exception {
    String key = newKey();
    String request =
        filtered().replace("\"filter\":{}", "\"filter\":{},\"excludedOrderIds\":[\"1\"]");
    exclusionFaults.arm(true);
    try {
      var unknown = post(request, key);
      assertThat(unknown.statusCode()).isEqualTo(503);
      assertThat(body(unknown).get("outcome").asText()).isEqualTo("UNKNOWN");
      assertThat(exclusionFaults.triggeredCount()).isEqualTo(1);
      assertThat(count("export_tasks")).isEqualTo(1);
      assertThat(count("export_task_orders")).isEqualTo(1);
      exclusionFaults.restore();
      var replay = post(request, key);
      assertThat(replay.statusCode()).isEqualTo(200);
      assertThat(body(replay).get("totalCount").asLong()).isEqualTo(3);
      assertThat(count("export_tasks")).isEqualTo(1);
      assertThat(count("command_outbox")).isEqualTo(1);
    } finally {
      exclusionFaults.restore();
    }
  }

  @Test
  void TC217_concurrentSameKeyCreatesOneTaskAndOneOriginalCommand() throws Exception {
    long tasks = count("export_tasks"), commands = count("command_outbox");
    String key = newKey();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CyclicBarrier(8);
      var futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 8; i++)
        futures.add(
            threads.submit(
                () -> {
                  start.await(5, TimeUnit.SECONDS);
                  var response = post(filtered(), key);
                  assertThat(response.statusCode()).isIn(200, 201);
                  return body(response).get("taskId").asText();
                }));
      var ids = new HashSet<String>();
      for (var future : futures) ids.add(future.get(15, TimeUnit.SECONDS));
      assertThat(ids).hasSize(1);
    }
    assertThat(count("export_tasks")).isEqualTo(tasks + 1);
    assertThat(count("command_outbox")).isEqualTo(commands + 1);
  }

  @Test
  void TC217_idOrderAndDuplicatesCanonicalizeButFieldOrderConflicts() throws Exception {
    String key = newKey();
    String first =
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"2\",\"1\",\"1\"]},\"fields\":[\"orderNo\",\"amount\"]}";
    var created = post(first, key);
    assertThat(created.statusCode()).isEqualTo(201);
    var replay = post(first.replace("[\"2\",\"1\",\"1\"]", "[\"1\",\"2\"]"), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(created));
    var conflict = post(first.replace("[\"orderNo\",\"amount\"]", "[\"amount\",\"orderNo\"]"), key);
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(body(conflict).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void TC217_equivalentKeywordWhitespaceAndDecimalFormsReplayOriginalTask() throws Exception {
    String key = newKey();
    var source = db.queryForMap("SELECT order_no,amount FROM orders WHERE id=1");
    String number = source.get("order_no").toString();
    String amount = ((java.math.BigDecimal) source.get("amount")).toPlainString();
    String first =
        json.writeValueAsString(
            Map.of(
                "scope",
                Map.of(
                    "type",
                    "FILTERED",
                    "filter",
                    Map.of(
                        "keyword",
                        " " + number.toUpperCase(Locale.ROOT) + " ",
                        "minAmount",
                        "0000.0",
                        "maxAmount",
                        "00" + amount)),
                "fields",
                List.of("orderNo")));
    String equivalent =
        json.writeValueAsString(
            Map.of(
                "fields",
                List.of("orderNo"),
                "scope",
                Map.of(
                    "filter",
                    Map.of(
                        "maxAmount",
                        amount,
                        "keyword",
                        number.toLowerCase(Locale.ROOT),
                        "minAmount",
                        "0.00"),
                    "type",
                    "FILTERED")));
    var created = post(first, key);
    assertThat(created.statusCode()).isEqualTo(201);
    var replay = post(equivalent, key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(created));
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @Test
  void TC217_changedScopeWithSameKeyConflictsWithoutCreatingAnotherTask() throws Exception {
    String key = newKey();
    var first = post(filtered(), key);
    assertThat(first.statusCode()).isEqualTo(201);
    var conflict =
        post(filtered().replace("\"filter\":{}", "\"filter\":{\"status\":\"PAID\"}"), key);
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(body(conflict).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }
}
