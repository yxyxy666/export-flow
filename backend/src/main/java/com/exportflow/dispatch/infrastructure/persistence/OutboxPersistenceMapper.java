// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.dispatch.infrastructure.persistence;

import com.exportflow.dispatch.domain.CommandOutbox;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** SQL时间显式按UTC解释；设计 function-design-reference.md。 */
@Component
public class OutboxPersistenceMapper {
  public CommandOutbox toDomain(CommandOutboxRecord row) {
    return new CommandOutbox(
        UUID.fromString(row.eventId()),
        UUID.fromString(row.taskId()),
        row.type(),
        row.schemaVersion(),
        row.status(),
        utc(row.acceptedAt()),
        utc(row.dispatchDeadline()),
        utc(row.nextAttemptAt()),
        row.attemptNo(),
        row.leaseOwner(),
        row.leaseToken() == null ? null : UUID.fromString(row.leaseToken()),
        utc(row.leaseUntil()));
  }

  private Instant utc(LocalDateTime value) {
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }
}
