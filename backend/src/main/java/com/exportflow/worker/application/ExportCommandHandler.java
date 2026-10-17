// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeSecurityTest.java](../../../../../../test/java/com/exportflow/RuntimeSecurityTest.java)、[BadCommandIsolationTest.java](../../../../../../test/java/com/exportflow/worker/BadCommandIsolationTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)。
package com.exportflow.worker.application;

import com.exportflow.dispatch.domain.CommandOutbox.ExportCommandEnvelope;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.worker.domain.ExecutionModels.*;
import com.exportflow.worker.port.ExportFileGenerator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 只有权威终态才ACK，UNKNOWN不发新命令；设计 contracts/worker-boundary.md，TC-223/225。 */
@Service
public class ExportCommandHandler {
  private final ExportTaskRepository tasks;
  private final TaskExecutionService executions;
  private final ExportFileGenerator generator;
  private final ExecutionSupervisor supervisor;
  private final com.exportflow.dispatch.port.CommandOutboxRepository outbox;

  public ExportCommandHandler(
      ExportTaskRepository tasks,
      TaskExecutionService executions,
      ExportFileGenerator generator,
      ExecutionSupervisor supervisor,
      com.exportflow.dispatch.port.CommandOutboxRepository outbox) {
    this.tasks = tasks;
    this.executions = executions;
    this.generator = generator;
    this.supervisor = supervisor;
    this.outbox = outbox;
  }

  public DeliveryDecision handle(ExportCommandEnvelope command, DeliveryContext delivery) {
    try {
      var registered = outbox.find(command.eventId());
      if (registered.isEmpty()
          || !registered.get().taskId().equals(command.taskId())
          || !registered.get().type().equals(command.type())
          || registered.get().schemaVersion() != command.schemaVersion())
        return DeliveryDecision.CLOSE_CHANNEL;
      var snapshot = tasks.findSnapshot(command.taskId());
      if (snapshot.isEmpty()) return DeliveryDecision.CLOSE_CHANNEL;
      var task = snapshot.get().task();
      if (task.status() == TaskStatus.SUCCEEDED || task.status() == TaskStatus.FAILED)
        return DeliveryDecision.ACK;
      if (task.status() == TaskStatus.PAUSED)
        return executions.reconcile(task.taskId(), "").kind() == ClaimKind.PAUSED
            ? DeliveryDecision.ACK
            : DeliveryDecision.HOLD;
      if (task.status() == TaskStatus.PAUSING) return DeliveryDecision.HOLD;
      if (!generator.isAvailable() || !supervisor.isAccepting())
        return DeliveryDecision.CLOSE_CHANNEL;
      String owner = UUID.randomUUID().toString();
      var claimed = executions.claim(command.taskId(), owner);
      if (claimed.kind() == ClaimKind.TERMINAL) return DeliveryDecision.ACK;
      if (claimed.kind() == ClaimKind.PAUSED) return DeliveryDecision.ACK;
      if (claimed.kind() != ClaimKind.ACQUIRED || claimed.lease() == null)
        return DeliveryDecision.HOLD;
      var guard = supervisor.onExecution(delivery, claimed.lease());
      var result =
          generator.generate(new GenerationContext(claimed.snapshot(), claimed.lease()), guard);
      // 用户暂停使旧guard失效，因此资源关闭证明必须先于普通失权HOLD分流。
      if (result.kind() == GenerationKind.PAUSE_STOPPED
          || guard.stopReason() == StopReason.USER_PAUSE) {
        if (!result.resourcesClosed()) return DeliveryDecision.HOLD;
        var control = executions.findControl(command.taskId());
        if (control == null || !"KNOWN".equals(control.readOutcome())) return DeliveryDecision.HOLD;
        var proof =
            result.pauseProof() != null
                ? result.pauseProof()
                : new PauseProof(
                    command.taskId(),
                    claimed.lease().owner(),
                    claimed.lease().fence(),
                    control.controlVersion(),
                    control.checkpointVersion());
        var paused = executions.acknowledgePause(proof);
        return Set.of("APPLIED", "ALREADY_PAUSED").contains(paused.kind())
            ? DeliveryDecision.ACK
            : DeliveryDecision.HOLD;
      }
      if (!guard.check()) return DeliveryDecision.HOLD;
      return handleGenerationResult(result, claimed.lease());
    } catch (RuntimeException unavailable) {
      return DeliveryDecision.HOLD;
    }
  }

  public DeliveryDecision handleGenerationResult(GenerationResult result, ExecutionLease lease) {
    if (result.kind() == GenerationKind.UNKNOWN) return DeliveryDecision.HOLD;
    if (result.kind() == GenerationKind.PAUSE_STOPPED) {
      if (!result.resourcesClosed() || result.pauseProof() == null) return DeliveryDecision.HOLD;
      var paused = executions.acknowledgePause(result.pauseProof());
      return java.util.Set.of("APPLIED", "ALREADY_PAUSED").contains(paused.kind())
          ? DeliveryDecision.ACK
          : DeliveryDecision.HOLD;
    }
    var completed = executions.complete(lease, result);
    if (completed == LeaseWriteResult.APPLIED) return DeliveryDecision.ACK;
    var reconciled = executions.reconcile(lease.taskId(), lease.owner());
    return reconciled.kind() == ClaimKind.TERMINAL ? DeliveryDecision.ACK : DeliveryDecision.HOLD;
  }
}
