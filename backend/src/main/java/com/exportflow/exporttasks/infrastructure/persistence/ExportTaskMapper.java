// 增量实施：[计划](../../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
// 职责：任务持久记录和 MyBatis 映射。
package com.exportflow.exporttasks.infrastructure.persistence;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.infrastructure.persistence.ExportTaskRecord.IdempotencyDbRecord;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 参数化持久入口；设计 function-design-reference.md，验证 TC-217/218。 */
@Mapper
public interface ExportTaskMapper {
  int insert(ExportTaskRecord task);

  int insertScopeOrderIds(@Param("taskId") String taskId, @Param("orderIds") List<Long> orderIds);

  Optional<ExportTaskRecord> findSummary(String taskId);

  Optional<ExportTaskRecord> findSnapshot(String taskId);

  List<Long> findScopeOrderIds(String taskId);

  ExportTaskRecord lockTask(String taskId);

  int failDispatchTimeout(@Param("taskId") String taskId, @Param("endedAt") Instant endedAt);

  Optional<IdempotencyDbRecord> findIdempotency(@Param("keyHash") byte[] keyHash);

  int insertIdempotency(
      @Param("submission") CanonicalSubmission submission, @Param("taskId") String taskId);
}
