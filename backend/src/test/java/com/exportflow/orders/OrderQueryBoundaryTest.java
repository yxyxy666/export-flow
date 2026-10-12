// 规格：[复用数据](../../../../../../../specs/002-order-management/provided-data.md)；[任务](../../../../../../../specs/002-order-management/tasks.md)。
package com.exportflow.orders;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

// [AC-001](../../../../../../../docs/product/acceptance-criteria.md#ac-001)
// [AC-002](../../../../../../../docs/product/acceptance-criteria.md#ac-002)
// [AC-004](../../../../../../../docs/product/acceptance-criteria.md#ac-004)
// [AC-021](../../../../../../../docs/product/acceptance-criteria.md#ac-021)
// [AC-022](../../../../../../../docs/product/acceptance-criteria.md#ac-022)
// [AC-023](../../../../../../../docs/product/acceptance-criteria.md#ac-023)
// [AC-024](../../../../../../../docs/product/acceptance-criteria.md#ac-024)
// [AC-034](../../../../../../../docs/product/acceptance-criteria.md#ac-034)
// [AC-035](../../../../../../../docs/product/acceptance-criteria.md#ac-035)

class OrderQueryBoundaryTest extends ProductionBackendFixture {
  @ParameterizedTest
  @ValueSource(ints = {20, 50, 100})
  void TC215_realPagesHaveNoMissingOrRepeatedIds(int size) throws Exception {
    useOrderVolume(251);
    var actual = new ArrayList<String>();
    for (int page = 1; page <= Math.ceil(251.0 / size); page++) {
      var result = body(get("/api/v1/orders?pageSize=" + size + "&page=" + page));
      assertThat(result.get("total").asLong()).isEqualTo(251);
      assertThat(result.get("page").asInt()).isEqualTo(page);
      assertThat(result.get("pageSize").asInt()).isEqualTo(size);
      result.get("items").forEach(row -> actual.add(row.get("id").asText()));
    }
    assertThat(actual)
        .containsExactlyElementsOf(
            db.queryForList(
                "SELECT CAST(id AS CHAR) FROM orders ORDER BY created_at DESC,id DESC",
                String.class));
  }

  @ParameterizedTest
  @CsvSource({
    "status,PENDING",
    "status,PAID",
    "status,SHIPPED",
    "status,COMPLETED",
    "status,CANCELED",
    "channel,WEB",
    "channel,APP",
    "channel,STORE",
    "channel,PARTNER"
  })
  void TC215_knownEnumFiltersReturnOnlyTheirOwnProvidedValues(String field, String value)
      throws Exception {
    var response = get("/api/v1/orders?" + field + "=" + value);
    assertThat(response.statusCode()).isEqualTo(200);
    var page = body(response);
    assertThat(page.get("total").asLong())
        .isEqualTo(
            db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE " + field + "=?", Long.class, value));
    for (var row : page.get("items")) assertThat(row.get(field).asText()).isEqualTo(value);
  }

  @Test
  void TC215_keywordMatchesProvidedNamePhoneAndOrderNumber() throws Exception {
    var source =
        db.queryForMap("SELECT order_no,customer_name,customer_phone FROM orders WHERE id=1");
    for (var value : source.values()) {
      String raw = value.toString();
      if (raw.isEmpty()) continue;
      String literal = "%" + raw.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      var page =
          body(
              get(
                  "/api/v1/orders?keyword="
                      + java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8)));
      assertThat(page.get("total").asLong())
          .isEqualTo(
              db.queryForObject(
                  "SELECT COUNT(*) FROM orders WHERE order_no COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!' OR customer_name COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!' OR customer_phone COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!'",
                  Long.class,
                  literal,
                  literal,
                  literal));
      assertThat(page.get("total").asLong()).isPositive();
    }
  }

  @Test
  void TC215_dtoPreservesProvidedMoneyUtcAndPhoneWithoutDataMutation() throws Exception {
    var source = db.queryForMap("SELECT * FROM orders WHERE id=1");
    String number =
        java.net.URLEncoder.encode(
            source.get("order_no").toString(), java.nio.charset.StandardCharsets.UTF_8);
    var response = get("/api/v1/orders?orderNo=" + number);
    var row = body(response).get("items").get(0);
    for (String field :
        List.of(
            "id",
            "orderNo",
            "customerName",
            "customerPhone",
            "status",
            "channel",
            "amount",
            "currency",
            "region",
            "createdAt")) assertThat(row.get(field).isTextual()).as(field).isTrue();
    assertThat(row.get("amount").asText())
        .isEqualTo(((java.math.BigDecimal) source.get("amount")).toPlainString());
    assertThat(row.get("createdAt").asText())
        .isEqualTo(
            ((java.time.LocalDateTime) source.get("created_at"))
                .toInstant(java.time.ZoneOffset.UTC)
                .toString());
    assertThat(row.get("customerPhone").asText()).isEqualTo(source.get("customer_phone"));
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
  }
}
