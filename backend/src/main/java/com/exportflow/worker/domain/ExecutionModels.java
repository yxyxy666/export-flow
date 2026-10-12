// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.domain;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskSnapshot;
import java.time.*;
import java.util.UUID;

/** 执行权、生成结果与交付裁决；设计 data-model.md，TC-224/225。 */
public final class ExecutionModels {
  private ExecutionModels() {}

  public record ExecutionPolicy(
      Duration leaseDuration,
      Duration heartbeatInterval,
      Duration claimTimeout,
      Duration maxAttemptDuration,
      int maxRecoveryTakeovers,
      Duration recoveryBackoffBase,
      Duration recoveryBackoffCap,
      Duration shutdownGrace) {
    public static ExecutionPolicy defaults() {
      return new ExecutionPolicy(
          Duration.ofSeconds(30),
          Duration.ofSeconds(5),
          Duration.ofSeconds(5),
          Duration.ofMinutes(5),
          3,
          Duration.ofSeconds(1),
          Duration.ofSeconds(30),
          Duration.ofSeconds(15));
    }
  }

  public record ExecutionLease(
      UUID taskId, String owner, long fence, Instant leaseUntil, long progressVersion) {}

  public enum ClaimKind {
    ACQUIRED,
    TERMINAL,
    BUSY,
    UNKNOWN
  }

  public record ClaimResult(ClaimKind kind, ExecutionLease lease, TaskSnapshot snapshot) {}

  public enum LeaseWriteResult {
    APPLIED,
    STALE,
    UNKNOWN
  }

  public record GenerationContext(TaskSnapshot snapshot, ExecutionLease lease) {}

  public enum GenerationKind {
    READY,
    KNOWN_FAILURE,
    UNKNOWN
  }

  public record GenerationResult(GenerationKind kind, ArtifactProof artifact, SafeFailure error) {}

  public record DeliveryContext(String channelId, long deliveryTag, Instant receivedAt) {}

  public enum DeliveryDecision {
    ACK,
    HOLD,
    CLOSE_CHANNEL
  }
}
