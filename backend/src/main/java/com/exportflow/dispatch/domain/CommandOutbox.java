// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OutboxDispatchMySqlTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxDispatchMySqlTest.java)、[OutboxFailureWindowsTest.java](../../../../../../test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java)。
package com.exportflow.dispatch.domain;

import java.time.*;
import java.util.*;
import java.util.function.DoubleSupplier;

/** 原始命令的持久投递权；设计 contracts/export-command.md，测试 TC-221/222。 */
public record CommandOutbox(
    UUID eventId,
    UUID taskId,
    String type,
    int schemaVersion,
    String status,
    Instant acceptedAt,
    Instant deadline,
    Instant nextAttemptAt,
    int attemptNo,
    String owner,
    UUID token,
    Instant leaseUntil) {
  public boolean canClaim(Instant now) {
    return now.isBefore(deadline)
        && !now.isBefore(nextAttemptAt)
        && (status.equals("READY") || status.equals("LEASED") && !now.isBefore(leaseUntil));
  }

  public boolean accepts(UUID candidate, Instant now) {
    return status.equals("LEASED") && Objects.equals(token, candidate) && now.isBefore(leaseUntil);
  }

  public static Duration backoffDelay(int attempt, DispatchPolicy policy, DoubleSupplier random) {
    if (attempt < 1) throw new IllegalArgumentException("尝试编号无效");
    long cap = policy.backoffCap().toMillis();
    long base = policy.backoffBase().toMillis();
    for (int index = 1; index < attempt && base < cap; index++)
      base = Math.min(cap, Math.multiplyExact(base, 2));
    double sample = random.getAsDouble();
    if (sample < 0 || sample >= 1 || !Double.isFinite(sample))
      throw new IllegalArgumentException("抖动无效");
    return Duration.ofMillis((long) (Math.min(base, cap) * sample));
  }

  public record DispatchPolicy(
      Duration scanInterval,
      int batchSize,
      int maxConcurrency,
      Duration publishBudget,
      Duration leaseDuration,
      Duration backoffBase,
      Duration backoffCap) {
    public static DispatchPolicy defaults() {
      return new DispatchPolicy(
          Duration.ofSeconds(1),
          20,
          2,
          Duration.ofSeconds(5),
          Duration.ofSeconds(15),
          Duration.ofSeconds(1),
          Duration.ofSeconds(30));
    }
  }

  public record DispatchLease(
      UUID eventId,
      UUID taskId,
      String owner,
      UUID token,
      int attemptNo,
      Instant leaseUntil,
      Instant deadline) {}

  public record PublishEvidence(boolean confirmAck, boolean correctRoute, boolean policyVerified) {
    public boolean isReliable() {
      return confirmAck && correctRoute && policyVerified;
    }
  }

  public enum PublishKind {
    CONFIRMED,
    FAILED,
    UNKNOWN
  }

  public record PublishResult(PublishKind kind, PublishEvidence evidence, String safeCode) {}

  public enum DispatchOutcome {
    SENT,
    RETRY_SCHEDULED,
    STALE,
    CLOSED,
    UNKNOWN
  }

  public record ClaimBatch(List<DispatchLease> leases) {
    public ClaimBatch {
      leases = List.copyOf(leases);
    }
  }

  public record DispatchScanResult(int claimed, int sent, int retried, int closed) {}

  public record DeadlineScanResult(int scanned, int failed, int closed) {}

  public record ExportCommandEnvelope(
      UUID eventId, UUID taskId, String type, int schemaVersion, String traceId) {}
}
