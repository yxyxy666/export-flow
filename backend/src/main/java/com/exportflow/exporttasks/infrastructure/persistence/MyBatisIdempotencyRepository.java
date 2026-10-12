// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
package com.exportflow.exporttasks.infrastructure.persistence;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.port.IdempotencyRepository;
import java.util.*;
import org.springframework.stereotype.Repository;

/** 持久唯一键裁决而非内存锁；设计 function-design-reference.md，验证 TC-217/218。 */
@Repository
public class MyBatisIdempotencyRepository implements IdempotencyRepository {
  private final ExportTaskMapper mapper;

  public MyBatisIdempotencyRepository(ExportTaskMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<IdempotencyRecord> findByKeyHash(byte[] keyHash) {
    return mapper
        .findIdempotency(keyHash)
        .map(
            row ->
                new IdempotencyRecord(
                    row.canonicalVersion(), row.requestHash(), UUID.fromString(row.taskId())));
  }

  public void insert(CanonicalSubmission submission, UUID taskId) {
    mapper.insertIdempotency(submission, taskId.toString());
  }
}
