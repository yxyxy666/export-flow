// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OutboxDispatchMySqlTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxDispatchMySqlTest.java)、[OutboxFailureWindowsTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java)。
package com.exportflow.dispatch.application;

import static com.exportflow.dispatch.domain.CommandOutbox.*;

import com.exportflow.dispatch.domain.CommandOutbox;
import com.exportflow.dispatch.port.*;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/** 领取容量受限，单条失败隔离，未知发送先核对；设计 function-design-reference.md，TC-221。 */
@Service
public class OutboxDispatchService {
  private final CommandOutboxRepository outbox;
  private final ExportCommandPublisher publisher;
  private final DispatchPolicy policy = DispatchPolicy.defaults();
  private final Semaphore scans = new Semaphore(1);
  private final String owner = UUID.randomUUID().toString();
  private final ThreadLocal<Boolean> sharedFailure = ThreadLocal.withInitial(() -> false);

  public OutboxDispatchService(CommandOutboxRepository outbox, ExportCommandPublisher publisher) {
    this.outbox = outbox;
    this.publisher = publisher;
  }

  public DispatchScanResult scanAvailable() {
    if (!scans.tryAcquire()) return new DispatchScanResult(0, 0, 0, 0);
    sharedFailure.set(false);
    try {
      var batch = outbox.claimDue(owner, policy.maxConcurrency(), policy);
      int sent = 0, retried = 0, closed = 0;
      for (var lease : batch.leases()) {
        var outcome = dispatchOne(lease);
        if (outcome == DispatchOutcome.SENT) sent++;
        if (outcome == DispatchOutcome.RETRY_SCHEDULED) retried++;
        if (outcome == DispatchOutcome.CLOSED) closed++;
        if (sharedFailure.get() || outcome == DispatchOutcome.UNKNOWN) break;
      }
      return new DispatchScanResult(batch.leases().size(), sent, retried, closed);
    } finally {
      sharedFailure.remove();
      scans.release();
    }
  }

  public DispatchOutcome dispatchOne(DispatchLease lease) {
    PublishEvidence evidence = null;
    try {
      if (!outbox.isPublishAllowed(lease)) return DispatchOutcome.STALE;
      var command = outbox.find(lease.eventId()).orElseThrow();
      var result =
          publisher.publish(
              new ExportCommandEnvelope(
                  lease.eventId(),
                  lease.taskId(),
                  command.type(),
                  command.schemaVersion(),
                  UUID.randomUUID().toString()),
              lease,
              policy.publishBudget());
      evidence = result.evidence();
      if (result.kind() == PublishKind.CONFIRMED && evidence != null && evidence.isReliable())
        return outbox.markSent(lease, evidence) ? DispatchOutcome.SENT : DispatchOutcome.STALE;
      if ("BROKER_UNAVAILABLE".equals(result.safeCode())
          || "TOPOLOGY_UNVERIFIED".equals(result.safeCode())) sharedFailure.set(true);
      return retry(lease, result.safeCode() == null ? "PUBLISH_UNKNOWN" : result.safeCode());
    } catch (IllegalArgumentException invalid) {
      return retry(lease, "COMMAND_INVALID");
    } catch (RuntimeException unknown) {
      return reconcile(lease, evidence);
    }
  }

  private DispatchOutcome retry(DispatchLease lease, String code) {
    try {
      var delay =
          CommandOutbox.backoffDelay(
              lease.attemptNo(), policy, () -> ThreadLocalRandom.current().nextDouble());
      return outbox.scheduleRetry(lease, code, delay)
          ? DispatchOutcome.RETRY_SCHEDULED
          : DispatchOutcome.STALE;
    } catch (RuntimeException unreadable) {
      return DispatchOutcome.UNKNOWN;
    }
  }

  public DispatchOutcome reconcile(DispatchLease lease, PublishEvidence evidence) {
    try {
      var row = outbox.find(lease.eventId()).orElseThrow();
      if (row.status().equals("SENT")) return DispatchOutcome.SENT;
      if (row.status().equals("CLOSED")) return DispatchOutcome.CLOSED;
      if (evidence != null && evidence.isReliable())
        return outbox.markSent(lease, evidence) ? DispatchOutcome.SENT : DispatchOutcome.STALE;
      return DispatchOutcome.UNKNOWN;
    } catch (RuntimeException unreadable) {
      return DispatchOutcome.UNKNOWN;
    }
  }
}
