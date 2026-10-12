// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

// [AC-061](../../../../../../../docs/product/acceptance-criteria.md#ac-061)
// [AC-064](../../../../../../../docs/product/acceptance-criteria.md#ac-064)
// [AC-050](../../../../../../../docs/product/acceptance-criteria.md#ac-050)
class CreationInputBoundaryTest extends ProductionBackendFixture {
  // 增量：[任务与证据](../../../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
  // [AC-079](../../../../../../../docs/product/acceptance-criteria.md#ac-079)
  // [AC-076](../../../../../../../docs/product/acceptance-criteria.md#ac-076)
  @ParameterizedTest
  @ValueSource(strings = {"[]", "[\"2\",\"1\",\"1\"]"})
  void TC312_validEmptyAndDuplicateExclusionsAreAccepted(String exclusions) throws Exception {
    var response = post(exclusionPayload(exclusions), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(body(response).get("scopeType").asText()).isEqualTo("FILTERED");
    assertThat(body(response).get("totalCount").asLong())
        .isEqualTo(exclusions.equals("[]") ? 4 : 2);
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(count("export_idempotency")).isEqualTo(1);
    assertThat(count("export_task_orders")).isEqualTo(exclusions.equals("[]") ? 0 : 2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "1",
        "{}",
        "[1]",
        "[null]",
        "[\"0\"]",
        "[\"-1\"]",
        "[\"01\"]",
        "[\"1.5\"]",
        "[\"9223372036854775808\"]"
      })
  void TC312_invalidExclusionStructureNeverWritesCreationRecords(String exclusions)
      throws Exception {
    var before = creationCounts();
    var response = post(exclusionPayload(exclusions), newKey());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(creationCounts()).isEqualTo(before);
  }

  @Test
  void TC312_selectedExclusionsAndUnknownScopeFieldsAreRejected() throws Exception {
    for (String payload :
        List.of(
            "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"],\"excludedOrderIds\":[]},\"fields\":[\"orderNo\"]}",
            exclusionPayload("[]")
                .replace("\"excludedOrderIds\":[]", "\"excludedOrderIds\":[],\"unknown\":true"))) {
      var before = creationCounts();
      assertThat(post(payload, newKey()).statusCode()).isEqualTo(400);
      assertThat(creationCounts()).isEqualTo(before);
    }
  }

  @Test
  void TC312_rawExclusionLengthIsCheckedBeforeDeduplication() throws Exception {
    // 请求长度边界使用重复的一个既有ID；不造来源数据或进行规模导出。
    String exact = "[" + String.join(",", Collections.nCopies(200000, "\"1\"")) + "]";
    var accepted = post(exclusionPayload(exact), newKey());
    assertThat(accepted.statusCode()).isEqualTo(201);
    assertThat(body(accepted).get("totalCount").asLong()).isEqualTo(3);
    assertThat(count("export_task_orders")).isEqualTo(1);
    var before = creationCounts();
    var rejected = post(exclusionPayload(exact.replaceFirst("\\]$", ",\"1\"]")), newKey());
    assertThat(rejected.statusCode()).isEqualTo(400);
    assertThat(creationCounts()).isEqualTo(before);
  }

  private String exclusionPayload(String exclusions) {
    return "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{},\"excludedOrderIds\":"
        + exclusions
        + "},\"fields\":[\"orderNo\"]}";
  }

  @Test
  void TC218_creationTransactionReturnsOnlyAfterAllParticipantsCommit() throws Exception {
    var dto =
        json.readValue(
            filtered(),
            com.exportflow.exporttasks.api.ExportTaskApiModels.CreateTaskRequestDto.class);
    var command =
        app.getBean(com.exportflow.exporttasks.api.ExportTaskApiMapper.class)
            .toCommand(dto, newKey());
    var submission =
        app.getBean(com.exportflow.exporttasks.application.ExportRequestCanonicalizer.class)
            .normalize(command);
    var result =
        app.getBean(com.exportflow.exporttasks.application.CreateExportTransaction.class)
            .commit(submission);
    assertThat(result.isReplay()).isFalse();
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("export_idempotency")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "short",
        "x xxxxxxxxxxxxxxxx",
        "xxxxxxxxxxxxxxxxé",
        "xxxxxxxxxxxxxxxx/",
        "xxxxxxxxxxxxxxxx?",
        "xxxxxxxxxxxxxxxx+"
      })
  void TC214_invalidIdempotencyKeyIsRejectedBeforeAnyCreation(String key) throws Exception {
    var before = creationCounts();
    var response = post(filtered(), key);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(creationCounts()).isEqualTo(before);
  }

  @Test
  void TC214_idempotencyKeyLengthEndpointsAndOneBeyond() throws Exception {
    for (int length : List.of(16, 128)) {
      assertThat(post(filtered(), "a".repeat(length)).statusCode()).isEqualTo(201);
    }
    var before = creationCounts();
    assertThat(post(filtered(), "a".repeat(129)).statusCode()).isEqualTo(400);
    assertThat(creationCounts()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "null",
        "{",
        "{\"scope\":null,\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"UNKNOWN\"},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[1]},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"9223372036854775808\"]},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"0\"]},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":null}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\"],\"status\":\"SUCCEEDED\"}"
      })
  void TC216_malformedCreateNeverLeavesTaskKeyOutboxOrSelection(String payload) throws Exception {
    var before = creationCounts();
    var response = post(payload, newKey());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(creationCounts()).isEqualTo(before);
    assertThat(response.body()).doesNotContain("java.", "SQLException", root.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"status\":\"ALIEN\"}",
        "{\"channel\":\"ALIEN\"}",
        "{\"minAmount\":\"1.001\"}",
        "{\"minAmount\":\"-1\"}",
        "{\"minAmount\":\"10000000000000000.00\"}",
        "{\"minAmount\":\"20\",\"maxAmount\":\"10\"}",
        "{\"createdFrom\":\"2026-02-30T00:00:00Z\"}",
        "{\"createdFrom\":\"2026-10-07T00:00:00+08:00\"}",
        "{\"createdFrom\":\"2026-10-08T00:00:00Z\",\"createdTo\":\"2026-10-07T00:00:00Z\"}",
        "{\"page\":2}",
        "{\"other\":true}"
      })
  void TC216_postFilterIndependentlyValidatesSameConstraints(String filter) throws Exception {
    var before = creationCounts();
    var response = post(filtered().replace("\"filter\":{}", "\"filter\":" + filter), newKey());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(creationCounts()).isEqualTo(before);
  }

  @Test
  void TC214_textLengthsAndUtf8BodyBudgetAreExact() throws Exception {
    for (var field : Map.of("keyword", 101, "orderNo", 65).entrySet()) {
      var counts = creationCounts();
      var response =
          post(
              filtered()
                  .replace(
                      "\"filter\":{}",
                      "\"filter\":{\""
                          + field.getKey()
                          + "\":\""
                          + "中".repeat(field.getValue())
                          + "\"}"),
              newKey());
      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(creationCounts()).isEqualTo(counts);
    }
    String valid = filtered();
    int budget = 16 * 1024 * 1024;
    String exact = valid + " ".repeat(budget - valid.getBytes(StandardCharsets.UTF_8).length);
    assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(budget);
    assertThat(post(exact, newKey()).statusCode()).isEqualTo(201);
    var before = creationCounts();
    var excess = post(exact + " ", newKey());
    assertThat(excess.statusCode()).isEqualTo(413);
    assertThat(body(excess).get("code").asText()).isEqualTo("REQUEST_TOO_LARGE");
    assertThat(creationCounts()).isEqualTo(before);
    // 多字节文本使UTF8长度超过预算而Java字符数仍小于预算。
    String unicode = filtered().replace("\"filter\":{}", "\"filter\":{\"keyword\":\"中\"}");
    unicode += " ".repeat(budget - unicode.length());
    assertThat(unicode.length()).isEqualTo(budget);
    assertThat(unicode.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(budget);
    assertThat(post(unicode, newKey()).statusCode()).isEqualTo(413);
    assertThat(creationCounts()).isEqualTo(before);
  }

  @Test
  void TC216_selectedAssociationIsExactDeduplicatedAndFilteredWritesNone() throws Exception {
    var selected =
        post(
            "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"2\",\"1\",\"1\"]},\"fields\":[\"amount\",\"orderNo\"]}",
            newKey());
    assertThat(selected.statusCode()).isEqualTo(201);
    String task = body(selected).get("taskId").asText();
    assertThat(
            db.queryForList(
                "SELECT order_id FROM export_task_orders WHERE task_id=? ORDER BY order_id",
                Long.class,
                task))
        .containsExactly(1L, 2L);
    assertThat(body(selected).get("totalCount").asLong()).isEqualTo(2);
    var filtered = post(filtered(), newKey());
    assertThat(filtered.statusCode()).isEqualTo(201);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_task_orders WHERE task_id=?",
                Long.class,
                body(filtered).get("taskId").asText()))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"export_task_orders", "export_idempotency"})
  void TC218_failureInEveryCreationParticipantRollsBackAllTables(String table) throws Exception {
    var before = creationCounts();
    try (var setup = mysql.getAdminConnection();
        var ddl = setup.createStatement()) {
      ddl.execute(
          "CREATE TRIGGER synthetic_fail_participant BEFORE INSERT ON "
              + table
              + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic participant failure'");
      try {
        var response =
            post(
                "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\",\"2\"]},\"fields\":[\"orderNo\"]}",
                newKey());
        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(creationCounts()).isEqualTo(before);
      } finally {
        ddl.execute("DROP TRIGGER synthetic_fail_participant");
      }
    }
  }

  @Test
  void TC217_concurrentDifferentDigestsSameKeyKeepOnlyWinner() throws Exception {
    String key = newKey();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CyclicBarrier(2);
      var a =
          threads.submit(
              () -> {
                start.await();
                return post(filtered(), key);
              });
      var b =
          threads.submit(
              () -> {
                start.await();
                return post(
                    filtered().replace("\"orderNo\",\"amount\",\"createdAt\"", "\"amount\""), key);
              });
      assertThat(
              List.of(
                  a.get(20, TimeUnit.SECONDS).statusCode(),
                  b.get(20, TimeUnit.SECONDS).statusCode()))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("export_idempotency")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @Test
  void TC217_emptyOptionalConditionsCanonicalizeAndOrderNumberCaseDoesNot() throws Exception {
    String key = newKey();
    var first = post(filtered(), key);
    var replay =
        post(
            filtered().replace("\"filter\":{}", "\"filter\":{\"keyword\":\" \",\"orderNo\":\"\"}"),
            key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(first));
    String orderKey = newKey();
    String orderNo = db.queryForObject("SELECT order_no FROM orders WHERE id=1", String.class);
    String request =
        filtered().replace("\"filter\":{}", "\"filter\":{\"orderNo\":\"" + orderNo + "\"}");
    assertThat(post(request, orderKey).statusCode()).isEqualTo(201);
    assertThat(
            post(request.replace(orderNo, orderNo.toLowerCase(java.util.Locale.ROOT)), orderKey)
                .statusCode())
        .isEqualTo(409);
  }
}
