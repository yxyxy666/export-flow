// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 只读订单领域模型；设计 data-model.md，验证 TC-215/233。 */
public record Order(
    long id,
    String orderNo,
    String customerName,
    String customerPhone,
    String status,
    String channel,
    BigDecimal amount,
    String currency,
    String region,
    Instant createdAt) {}
