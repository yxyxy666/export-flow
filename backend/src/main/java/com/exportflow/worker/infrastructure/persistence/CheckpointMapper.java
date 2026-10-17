// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.infrastructure.persistence;

import com.exportflow.worker.infrastructure.persistence.CheckpointRecord.BlockRecord;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 短事务登记不可变引用；设计 function-design-reference.md，TC-235。 */
@Mapper
public interface CheckpointMapper {
  @Select(
      "SELECT COUNT(*)=1 FROM export_checkpoints c JOIN export_tasks t ON t.task_id=c.task_id WHERE c.task_id=#{taskId} AND c.data_generation=#{generation} AND c.manifest_sealed=TRUE AND c.manifest_row_count=t.total_count AND c.verified_prefix_count=t.total_count AND t.trusted_processed_count=t.total_count AND t.execution_phase='ASSEMBLING' AND c.segment_count>0 AND c.segment_count=(SELECT COUNT(*) FROM export_data_segments s WHERE s.task_id=c.task_id AND s.data_generation=c.data_generation) AND NOT EXISTS(SELECT 1 FROM export_data_segments s LEFT JOIN export_data_blocks b ON b.block_id=s.active_block_id AND b.task_id=s.task_id AND b.data_generation=s.data_generation AND b.sequence=s.sequence WHERE s.task_id=c.task_id AND s.data_generation=c.data_generation AND (s.state<>'VERIFIED' OR b.block_id IS NULL OR b.row_count<>s.row_count OR b.configuration_hash<>s.configuration_hash))")
  boolean fullyTrusted(@Param("taskId") String taskId, @Param("generation") long generation);

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
