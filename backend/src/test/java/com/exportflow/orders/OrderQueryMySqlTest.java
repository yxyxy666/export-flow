// 规格：[复用数据](../../../../../../../specs/002-order-management/provided-data.md)；[任务](../../../../../../../specs/002-order-management/tasks.md)。
package com.exportflow.orders;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import org.junit.jupiter.api.Test;
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

class OrderQueryMySqlTest extends ProductionBackendFixture {
  @Test
  void TC215_defaultAndAscendingUseStableTimeAndIdOrder() throws Exception {
    var page = body(get("/api/v1/orders"));
    assertThat(page.get("page").asInt()).isEqualTo(1);
    assertThat(page.get("pageSize").asInt()).isEqualTo(20);
    assertThat(page.get("total").asLong()).isEqualTo(count("orders"));
    assertThat(ids(page))
        .containsExactlyElementsOf(
            db.queryForList(
                "SELECT CAST(id AS CHAR) FROM orders ORDER BY created_at DESC,id DESC",
                String.class));
    assertThat(ids(body(get("/api/v1/orders?sortDirection=ASC"))))
        .containsExactlyElementsOf(
            db.queryForList(
                "SELECT CAST(id AS CHAR) FROM orders ORDER BY created_at,id", String.class));
  }

  @Test
  void TC215_orderNumberIsExactAndCaseSensitiveOnProvidedData() throws Exception {
    String number = db.queryForObject("SELECT order_no FROM orders WHERE id=1", String.class);
    for (String value :
        List.of(
            number, number.toLowerCase(Locale.ROOT), number.substring(0, number.length() - 1))) {
      var response = get("/api/v1/orders?orderNo=" + encode(value));
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(ids(body(response)))
          .containsExactlyElementsOf(
              db.queryForList(
                  "SELECT CAST(id AS CHAR) FROM orders WHERE order_no COLLATE utf8mb4_bin=? ORDER BY created_at DESC,id DESC",
                  String.class,
                  value));
    }
  }

  @Test
  void TC215_inclusiveMoneyAndUtcBoundsUseProvidedValues() throws Exception {
    var source = db.queryForMap("SELECT amount,created_at FROM orders WHERE id=1");
    String amount = ((java.math.BigDecimal) source.get("amount")).toPlainString();
    String instant =
        ((java.time.LocalDateTime) source.get("created_at"))
            .toInstant(java.time.ZoneOffset.UTC)
            .toString();
    var page =
        body(
            get(
                "/api/v1/orders?minAmount="
                    + amount
                    + "&maxAmount="
                    + amount
                    + "&createdFrom="
                    + instant
                    + "&createdTo="
                    + instant));
    assertThat(ids(page))
        .containsExactlyElementsOf(
            db.queryForList(
                "SELECT CAST(id AS CHAR) FROM orders WHERE amount=? AND created_at=? ORDER BY created_at DESC,id DESC",
                String.class,
                source.get("amount"),
                source.get("created_at")));
    assertThat(ids(page)).contains("1");
  }

  @Test
  void TC215_combinedStatusChannelAndLiteralKeyword() throws Exception {
    var row = db.queryForMap("SELECT order_no,status,channel FROM orders WHERE id=1");
    var page =
        body(
            get(
                "/api/v1/orders?keyword="
                    + encode(row.get("order_no").toString())
                    + "&status="
                    + row.get("status")
                    + "&channel="
                    + row.get("channel")));
    assertThat(ids(page)).contains("1");
    for (var item : page.get("items")) {
      assertThat(item.get("status").asText()).isEqualTo(row.get("status"));
      assertThat(item.get("channel").asText()).isEqualTo(row.get("channel"));
    }
  }

  @Test
  void TC215_emptyPageStillReturnsAuthoritativeCount() throws Exception {
    var page = body(get("/api/v1/orders?page=2&pageSize=20"));
    assertThat(page.get("items").isEmpty()).isTrue();
    assertThat(page.get("total").asLong()).isEqualTo(count("orders"));
  }

  private List<String> ids(com.fasterxml.jackson.databind.JsonNode page) {
    var result = new ArrayList<String>();
    page.get("items").forEach(item -> result.add(item.get("id").asText()));
    return result;
  }

  private String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
