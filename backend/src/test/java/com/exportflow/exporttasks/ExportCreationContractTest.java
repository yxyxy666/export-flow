// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-006](../../../../../../../docs/product/acceptance-criteria.md#ac-006)
// [AC-008](../../../../../../../docs/product/acceptance-criteria.md#ac-008)
// [AC-009](../../../../../../../docs/product/acceptance-criteria.md#ac-009)
// [AC-019](../../../../../../../docs/product/acceptance-criteria.md#ac-019)
// [AC-060](../../../../../../../docs/product/acceptance-criteria.md#ac-060)
// [AC-061](../../../../../../../docs/product/acceptance-criteria.md#ac-061)
class ExportCreationContractTest extends ProductionBackendFixture {
  // [AC-011](../../../../../../../docs/product/acceptance-criteria.md#ac-011)
  @Test
  void TC216_twoHundredThousandSelectedIsAcceptedButLargerFilteredScopeIsRejected()
      throws Exception {
    useOrderVolume(200001);
    var oversized =
        post(
            "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\"]}", newKey());
    assertThat(oversized.statusCode()).isEqualTo(422);
    assertThat(body(oversized).get("code").asText()).isEqualTo("EXPORT_LIMIT_EXCEEDED");
    assertThat(body(oversized).path("details").path("currentCount").asLong()).isEqualTo(200001);
    assertThat(body(oversized).path("details").path("limit").asLong()).isEqualTo(200000);
    var ids =
        db.queryForList(
            "SELECT CAST(id AS CHAR) FROM orders ORDER BY id LIMIT 200000", String.class);
    var request =
        json.writeValueAsString(
            java.util.Map.of(
                "scope",
                java.util.Map.of("type", "SELECTED", "orderIds", ids),
                "fields",
                java.util.List.of(
                    "orderNo",
                    "customerName",
                    "customerPhone",
                    "status",
                    "channel",
                    "amount",
                    "currency",
                    "region",
                    "createdAt")));
    assertThat(request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThan(16 * 1024 * 1024);
    var accepted = post(request, newKey());
    assertThat(accepted.statusCode()).isEqualTo(201);
    assertThat(body(accepted).get("totalCount").asLong()).isEqualTo(200000);
    assertThat(state(java.util.UUID.fromString(body(accepted).get("taskId").asText())))
        .isEqualTo("PENDING");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[]},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[]}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"unknown\"]}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\",\"orderNo\"]}",
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"],\"filter\":{}},\"fields\":[\"orderNo\"]}",
        "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\"],\"total\":4}"
      })
  void TC216_invalidScopeAndFieldsLeaveNoPartialRecords(String request) throws Exception {
    long tasks = count("export_tasks"),
        outbox = count("command_outbox"),
        keys = count("export_idempotency");
    var response = post(request, newKey());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(count("export_tasks")).isEqualTo(tasks);
    assertThat(count("command_outbox")).isEqualTo(outbox);
    assertThat(count("export_idempotency")).isEqualTo(keys);
  }

  @Test
  void TC216_missingSelectedIdRejectsWholeRequestAndEmptyFilterIsRejected() throws Exception {
    var missing =
        post(
            "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\",\"999\"]},\"fields\":[\"orderNo\"]}",
            newKey());
    assertThat(missing.statusCode()).isEqualTo(422);
    assertThat(body(missing).get("code").asText()).isEqualTo("ORDER_SELECTION_INVALID");
    var empty =
        post(
            "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{\"orderNo\":\"ABSENT\"}},\"fields\":[\"orderNo\"]}",
            newKey());
    assertThat(empty.statusCode()).isEqualTo(400);
    assertThat(body(empty).get("code").asText()).isEqualTo("EMPTY_EXPORT_SCOPE");
  }

  @Test
  void TC214_over16MiBIsRejectedBeforeTransactionIncludingTrailingContent() throws Exception {
    long before = count("export_tasks");
    var response = post(filtered() + " ".repeat(16 * 1024 * 1024), newKey());
    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(body(response).get("code").asText()).isEqualTo("REQUEST_TOO_LARGE");
    assertThat(body(response).get("outcome").asText()).isEqualTo("REJECTED");
    assertThat(count("export_tasks")).isEqualTo(before);
  }
}
