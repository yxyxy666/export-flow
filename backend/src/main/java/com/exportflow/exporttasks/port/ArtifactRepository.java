// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.exporttasks.port;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.worker.domain.ExecutionModels.*;
import java.time.Instant;
import java.util.*;

/** 文件就绪、成功采用及清理资格共用权威裁决；设计 function-design-reference.md。 */
public interface ArtifactRepository {
  Optional<ArtifactProof> findReady(UUID taskId, String configurationHash);

  Optional<ArtifactProof> findAdopted(UUID taskId);

  LeaseWriteResult registerReady(ExecutionLease lease, ArtifactProof proof);

  Optional<CleanupPermit> claimCleanup(StorageCandidate candidate, Instant now);

  void completeCleanup(CleanupPermit permit);
}
