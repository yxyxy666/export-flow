// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.orders;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-005](../../../../../../../docs/product/acceptance-criteria.md#ac-005)
// [AC-061](../../../../../../../docs/product/acceptance-criteria.md#ac-061)
class OrderApiContractTest extends ProductionBackendFixture {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "page=0",
        "page=1.5",
        "page=1000001",
        "pageSize=30",
        "status=ALIEN",
        "channel=ALIEN",
        "sortDirection=RANDOM",
        "minAmount=-1",
        "minAmount=1.001",
        "minAmount=20&maxAmount=10",
        "createdFrom=2026-10-07T00:00:00%2B08:00",
        "createdFrom=2026-10-08T00:00:00Z&createdTo=2026-10-07T00:00:00Z",
        "unknown=true",
        "page=1&page=2"
      })
  void TC214_invalidQueryIsRejectedWithoutLeakingInternalDetails(String query) throws Exception {
    var response = get("/api/v1/orders?" + query);
    assertThat(response.statusCode()).isEqualTo(400);
    var error = body(response);
    assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(error.get("traceId").asText()).isNotBlank();
    assertThat(response.body())
        .doesNotContain("SQLException", "com.exportflow", "jdbc:mysql", "synthetic-test-only");
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
  }
}
