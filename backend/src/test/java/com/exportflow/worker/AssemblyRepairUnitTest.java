// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.orders.domain.Order;
import com.exportflow.worker.infrastructure.file.DataBlockCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// [AC-088](../../../../../../../docs/product/acceptance-criteria.md#ac-088)
// [AC-089](../../../../../../../docs/product/acceptance-criteria.md#ac-089)
class AssemblyRepairUnitTest {
  private final DataBlockCodec codec =
      new DataBlockCodec(new ObjectMapper().findAndRegisterModules());

  private Order row(long id, String name) {
    return new Order(
        id,
        "ORDER-" + id,
        name,
        "0013800000000",
        "PAID",
        "WEB",
        new BigDecimal("1.00"),
        "CNY",
        "香港",
        Instant.parse("2026-10-17T00:00:00Z"));
  }

  private String checksum(List<Order> rows) throws Exception {
    return (String)
        DataBlockCodec.class
            .getMethod("checksumRows", List.class, int.class)
            .invoke(codec, rows, 1);
  }

  @Test
  void TC414_contentDigestIsStableWithoutDependingOnNewBlockIdentity() throws Exception {
    var rows = List.of(row(1, "甲"), row(2, "乙"));
    assertThat(checksum(rows)).matches("[0-9a-f]{64}").isEqualTo(checksum(List.copyOf(rows)));
  }

  @Test
  void TC415_sourceContentOrOrderChangeCannotMasqueradeAsOriginalSegment() throws Exception {
    var original = checksum(List.of(row(1, "甲"), row(2, "乙")));
    assertThat(checksum(List.of(row(1, "变化"), row(2, "乙")))).isNotEqualTo(original);
    assertThat(checksum(List.of(row(2, "乙"), row(1, "甲")))).isNotEqualTo(original);
    assertThat(checksum(List.of(row(1, "甲")))).isNotEqualTo(original);
  }
}
