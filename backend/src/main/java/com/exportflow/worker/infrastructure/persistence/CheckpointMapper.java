// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import com.exportflow.worker.infrastructure.persistence.CheckpointRecord.BlockRecord;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 短事务登记不可变引用；设计 function-design-reference.md，TC-235。 */
@Mapper
public interface CheckpointMapper {
  CheckpointRecord find(@Param("taskId") String taskId);

  CheckpointRecord lock(@Param("taskId") String taskId);

  List<BlockRecord> findBlocks(
      @Param("taskId") String taskId, @Param("generation") long generation);

  Optional<BlockRecord> findBlock(@Param("taskId") String taskId, @Param("blockId") String blockId);

  int initialize(@Param("taskId") String taskId, @Param("hash") String hash);

  int insertBlock(BlockRecord record);

  int updateCheckpoint(
      @Param("taskId") String taskId,
      @Param("count") long count,
      @Param("time") Instant time,
      @Param("id") long id);

  int startGeneration(@Param("taskId") String taskId);
}
