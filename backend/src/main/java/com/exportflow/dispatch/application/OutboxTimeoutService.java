// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[DispatchSchedulingTest.java](../../../../../../test/java/com/exportflow/dispatch/DispatchSchedulingTest.java)、[OutboxDeadlineRaceTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java)、[OutboxTimeoutMySqlTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java)。
package com.exportflow.dispatch.application;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.exportflow.dispatch.port.CommandOutboxRepository;
import org.springframework.stereotype.Service;

/** 与发布线程独立，期限不受租约或退避影响；设计 function-design-reference.md，TC-222。 */
@Service
public class OutboxTimeoutService {
  private final CommandOutboxRepository outbox;

  public OutboxTimeoutService(CommandOutboxRepository outbox) {
    this.outbox = outbox;
  }

  public DeadlineScanResult scanExpired() {
    var candidates = outbox.findExpired(20);
    int closed = 0;
    for (var taskId : candidates)
      if (outbox.expireOrClose(taskId) == DispatchOutcome.CLOSED) closed++;
    return new DeadlineScanResult(candidates.size(), closed, closed);
  }
}
