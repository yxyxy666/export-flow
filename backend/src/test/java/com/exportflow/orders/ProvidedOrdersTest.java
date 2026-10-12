// 规格：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.orders;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-001](../../../../../../../docs/product/acceptance-criteria.md#ac-001)
// [AC-061](../../../../../../../docs/product/acceptance-criteria.md#ac-061)
class ProvidedOrdersTest extends ProductionBackendFixture {
  @Override
  protected void selectProvidedOrders() {}

  @Test
  void TC215_queriesReuseProvidedRowsWithoutCreatingOrCopyingOrders() throws Exception {
    long source = db.queryForObject("SELECT COUNT(*) FROM exportflow.orders", Long.class);
    assertThat(source).isPositive();
    assertThat(count("orders")).isEqualTo(source);
    assertThat(
            db.queryForObject(
                "SELECT TABLE_TYPE FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders'",
                String.class))
        .isEqualTo("VIEW");
    var page = body(get("/api/v1/orders"));
    assertThat(page.get("total").asLong()).isEqualTo(source);
    var row = page.get("items").get(0);
    var original =
        db.queryForMap("SELECT * FROM exportflow.orders WHERE id=?", row.get("id").asText());
    assertThat(row.get("orderNo").asText()).isEqualTo(original.get("order_no"));
    assertThat(row.get("amount").asText())
        .isEqualTo(((java.math.BigDecimal) original.get("total_amount")).toPlainString());
    assertThat(row.get("channel").asText()).isEqualTo(original.get("sales_channel"));
    assertThat(row.get("region").asText()).isEqualTo(original.get("shipping_province"));
  }

  @Test
  void TC215_orderSourceRemainsReadOnlyForApplicationAndFixture() {
    assertThatThrownBy(() -> db.update("UPDATE orders SET customer_name=customer_name WHERE id=1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> db.update("DELETE FROM orders WHERE id=-1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> db.update("UPDATE exportflow.orders SET customer_name=customer_name WHERE id=1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
