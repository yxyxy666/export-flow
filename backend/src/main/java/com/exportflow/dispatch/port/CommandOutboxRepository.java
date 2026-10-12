// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
// 职责：Outbox 事务读写与条件裁决端口。
package com.exportflow.dispatch.port;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.exportflow.dispatch.domain.CommandOutbox;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.UUID;

/** 原始命令和创建事务一致；设计 function-design-reference.md，验证 TC-218。 */
public interface CommandOutboxRepository {
  void insert(UUID taskId, UUID eventId, Instant acceptedAt);

  ClaimBatch claimDue(String owner, int capacity, DispatchPolicy policy);

  boolean isPublishAllowed(DispatchLease lease);

  boolean markSent(DispatchLease lease, PublishEvidence evidence);

  boolean scheduleRetry(DispatchLease lease, String safeCode, Duration delay);

  Optional<CommandOutbox> find(UUID eventId);

  List<UUID> findExpired(int limit);

  DispatchOutcome expireOrClose(UUID taskId);
}
