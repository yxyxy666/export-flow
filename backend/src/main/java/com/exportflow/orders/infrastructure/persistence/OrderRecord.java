// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.orders.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DATETIME(0)数据库记录，转换时显式指定UTC。 */
public record OrderRecord(
    long id,
    String orderNo,
    String customerName,
    String customerPhone,
    String status,
    String channel,
    BigDecimal amount,
    String currency,
    String region,
    LocalDateTime createdAt) {}
