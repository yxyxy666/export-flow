// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[RuntimeCapabilityTest.java](../../../../../../test/java/com/exportflow/RuntimeCapabilityTest.java)、[DeliveryIsolationUnitTest.java](../../../../../../test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java)、[DurabilityGuardTest.java](../../../../../../test/java/com/exportflow/worker/DurabilityGuardTest.java)。
package com.exportflow.worker.application;

import static com.exportflow.worker.domain.ExecutionModels.*;

import com.exportflow.worker.port.TaskExecutionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 事务异常后只依据已提交权威状态核对；设计 function-design-reference.md，TC-224/225。 */
@Service
public class TaskExecutionService {
  private final TaskExecutionRepository executions;

  public TaskExecutionService(TaskExecutionRepository executions) {
    this.executions = executions;
  }

  public ClaimResult claim(UUID taskId, String owner) {
    try {
      return executions.claim(taskId, owner, ExecutionPolicy.defaults());
    } catch (RuntimeException unknown) {
      return reconcile(taskId, owner);
    }
  }

  public LeaseWriteResult renew(ExecutionLease lease) {
    try {
      return executions.renew(lease);
    } catch (RuntimeException unknown) {
      return LeaseWriteResult.UNKNOWN;
    }
  }

  public LeaseWriteResult complete(ExecutionLease lease, GenerationResult result) {
    try {
      return executions.complete(lease, result);
    } catch (RuntimeException unknown) {
      return LeaseWriteResult.UNKNOWN;
    }
  }

  public ClaimResult reconcile(UUID taskId, String owner) {
    try {
      var result = executions.find(taskId);
      if (result.kind() == ClaimKind.ACQUIRED
          && (result.lease() == null || !result.lease().owner().equals(owner)))
        return new ClaimResult(ClaimKind.BUSY, null, result.snapshot());
      return result;
    } catch (RuntimeException unreadable) {
      return new ClaimResult(ClaimKind.UNKNOWN, null, null);
    }
  }
}
