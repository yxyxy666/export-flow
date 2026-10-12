// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
// 职责：Outbox 持久端口适配。
package com.exportflow.dispatch.infrastructure.persistence;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.exportflow.dispatch.domain.CommandOutbox;
import com.exportflow.dispatch.port.CommandOutboxRepository;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 写原始命令加入同一事务；设计 function-design-reference.md，验证 TC-218。 */
@Repository
public class MyBatisCommandOutboxRepository implements CommandOutboxRepository {
  private final CommandOutboxMapper mapper;
  private final OutboxPersistenceMapper converter;
  private final ExportTaskRepository tasks;
  private final TransactionTemplate transaction;

  public MyBatisCommandOutboxRepository(
      CommandOutboxMapper mapper,
      OutboxPersistenceMapper converter,
      ExportTaskRepository tasks,
      PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.converter = converter;
    this.tasks = tasks;
    transaction = new TransactionTemplate(manager);
    transaction.setTimeout(5);
  }

  public void insert(UUID taskId, UUID eventId, Instant acceptedAt) {
    mapper.insert(taskId.toString(), eventId.toString(), acceptedAt);
  }

  public ClaimBatch claimDue(String owner, int capacity, DispatchPolicy policy) {
    if (owner == null || owner.isBlank() || owner.length() > 128 || capacity < 0)
      throw new IllegalArgumentException("投递领取参数无效");
    if (capacity == 0) return new ClaimBatch(List.of());
    int limit = Math.min(capacity, policy.batchSize());
    List<DispatchLease> leases = new ArrayList<>();
    for (String candidate : mapper.findDue(limit)) {
      DispatchLease lease =
          transaction.execute(
              status -> {
                UUID taskId = UUID.fromString(candidate);
                var task = tasks.lockTask(taskId);
                var record = mapper.lockByTask(candidate);
                if (task == null || record == null) return null;
                var row = converter.toDomain(record);
                Instant now = mapper.databaseNow();
                if (task.status() != TaskStatus.PENDING || !row.canClaim(now)) return null;
                int attempt = Math.incrementExact(row.attemptNo());
                UUID token = UUID.randomUUID();
                Instant until = now.plus(policy.leaseDuration());
                Instant retryAt =
                    until.plus(
                        CommandOutbox.backoffDelay(
                            attempt, policy, () -> ThreadLocalRandom.current().nextDouble()));
                if (row.token() != null)
                  mapper.finishAttempt(
                      row.eventId().toString(),
                      row.attemptNo(),
                      row.token().toString(),
                      "LEASE_EXPIRED",
                      null,
                      now);
                mapper.reserveAttempt(
                    row.eventId().toString(), owner, token.toString(), attempt, until, retryAt);
                mapper.insertAttempt(
                    row.eventId().toString(), attempt, token.toString(), now, retryAt);
                return new DispatchLease(
                    row.eventId(), taskId, owner, token, attempt, until, row.deadline());
              });
      if (lease != null) leases.add(lease);
    }
    return new ClaimBatch(leases);
  }

  public boolean isPublishAllowed(DispatchLease lease) {
    var row = find(lease.eventId());
    Instant now = mapper.databaseNow();
    return row.isPresent()
        && row.get().accepts(lease.token(), now)
        && now.isBefore(row.get().deadline())
        && tasks
            .findSummary(lease.taskId())
            .map(task -> task.status() == TaskStatus.PENDING)
            .orElse(false);
  }

  public boolean markSent(DispatchLease lease, PublishEvidence evidence) {
    if (evidence == null || !evidence.isReliable()) return false;
    return Boolean.TRUE.equals(
        transaction.execute(
            status -> {
              var task = tasks.lockTask(lease.taskId());
              var record = mapper.lockByTask(lease.taskId().toString());
              if (task == null || record == null) return false;
              var row = converter.toDomain(record);
              Instant now = mapper.databaseNow();
              if (task.status() != TaskStatus.PENDING
                  || !row.accepts(lease.token(), now)
                  || !now.isBefore(row.deadline())) return false;
              boolean changed =
                  mapper.markSent(lease.eventId().toString(), lease.token().toString(), now) == 1;
              if (changed)
                mapper.finishAttempt(
                    lease.eventId().toString(),
                    lease.attemptNo(),
                    lease.token().toString(),
                    "SENT",
                    null,
                    now);
              return changed;
            }));
  }

  public boolean scheduleRetry(DispatchLease lease, String safeCode, Duration delay) {
    if (delay.isNegative() || safeCode == null || !safeCode.matches("[A-Z0-9_]{1,64}"))
      throw new IllegalArgumentException("重试参数无效");
    return Boolean.TRUE.equals(
        transaction.execute(
            status -> {
              var task = tasks.lockTask(lease.taskId());
              var record = mapper.lockByTask(lease.taskId().toString());
              if (task == null || record == null) return false;
              var row = converter.toDomain(record);
              Instant now = mapper.databaseNow();
              if (task.status() != TaskStatus.PENDING
                  || !row.accepts(lease.token(), now)
                  || !now.isBefore(row.deadline())) return false;
              boolean changed =
                  mapper.scheduleRetry(
                          lease.eventId().toString(),
                          lease.token().toString(),
                          safeCode,
                          now.plus(delay))
                      == 1;
              if (changed)
                mapper.finishAttempt(
                    lease.eventId().toString(),
                    lease.attemptNo(),
                    lease.token().toString(),
                    "RETRY",
                    safeCode,
                    now);
              return changed;
            }));
  }

  public Optional<CommandOutbox> find(UUID eventId) {
    return mapper.find(eventId.toString()).map(converter::toDomain);
  }

  public List<UUID> findExpired(int limit) {
    if (limit < 1 || limit > 20) throw new IllegalArgumentException("到期扫描批量无效");
    return mapper.findExpired(limit).stream().map(UUID::fromString).toList();
  }

  public DispatchOutcome expireOrClose(UUID taskId) {
    return transaction.execute(
        status -> {
          var task = tasks.lockTask(taskId);
          var record = mapper.lockByTask(taskId.toString());
          if (task == null || record == null) return DispatchOutcome.STALE;
          var row = converter.toDomain(record);
          Instant now = mapper.databaseNow();
          if (row.status().equals("SENT") || row.status().equals("CLOSED"))
            return DispatchOutcome.STALE;
          String reason;
          if (task.status() != TaskStatus.PENDING) reason = "AUTHORITY_NO_LONGER_NEEDS_COMMAND";
          else {
            if (now.isBefore(row.deadline())) return DispatchOutcome.STALE;
            if (!tasks.failDispatchTimeout(taskId, now)) return DispatchOutcome.STALE;
            reason = "TASK_DISPATCH_TIMEOUT";
          }
          mapper.close(row.eventId().toString(), reason, now);
          if (row.token() != null)
            mapper.finishAttempt(
                row.eventId().toString(),
                row.attemptNo(),
                row.token().toString(),
                "CLOSED",
                reason,
                now);
          return DispatchOutcome.CLOSED;
        });
  }
}
