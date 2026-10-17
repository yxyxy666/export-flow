// 设计：[选择性修复事务](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.worker.port;

import com.exportflow.worker.domain.AssemblyRepairModels.*;
import com.exportflow.worker.domain.ExecutionModels.ExecutionLease;
import java.util.*;

public interface AssemblyRepairRepository {
  RepairSnapshot load(UUID taskId);

  RepairWriteResult markChecking(ExecutionLease lease);

  RepairWriteResult sealManifest(
      ExecutionLease lease, RepairSnapshot expected, List<SegmentBaseline> baselines);

  RepairWriteResult beginRepair(ExecutionLease lease, AssemblyAudit audit);

  RepairWriteResult commitReplacement(
      ExecutionLease lease, RepairPlan plan, SegmentRef segment, VerifiedBlock block);

  Optional<VerifiedBlock> findReplacement(UUID taskId, long epoch, int sequence);

  RepairWriteResult markAssembling(ExecutionLease lease, AssemblyAudit audit);
}
