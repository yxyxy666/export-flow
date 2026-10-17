// 设计：[清单及修复SQL](../../../../../../../../../specs/004-task-management/data-model.md)。
package com.exportflow.worker.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AssemblyRepairMapper {
  @Select(
      "SELECT state FROM export_task_repairs WHERE task_id=#{id} ORDER BY repair_epoch FOR UPDATE")
  List<String> lockTaskRepairs(String id);

  @Update(
      "UPDATE export_task_repairs SET state=#{state},finished_at=UTC_TIMESTAMP(3) WHERE task_id=#{id} AND finished_at IS NULL")
  int finishRepairs(@Param("id") String id, @Param("state") String state);

  SnapshotRow snapshot(String id);

  @Update(
      "UPDATE export_tasks SET execution_phase='CHECKING',repair_state=IF(repair_attempt>0,'REBUILDING','CHECKING'),version=version+1 WHERE task_id=#{id}")
  int checking(String id);

  @Select(
      "SELECT state FROM export_task_repairs WHERE task_id=#{id} AND repair_epoch=#{epoch} FOR UPDATE")
  String lockRepair(@Param("id") String id, @Param("epoch") long epoch);

  @Select(
      "SELECT task_id,data_generation,sequence,previous_cursor_json,last_cursor_json,row_count,configuration_hash,format_version,content_checksum,active_block_id,selection_version,verified_repair_epoch,state FROM export_data_segments WHERE task_id=#{id} AND data_generation=#{generation} ORDER BY sequence FOR UPDATE")
  List<SegmentRow> lockSegments(@Param("id") String id, @Param("generation") long generation);

  @Insert(
      "INSERT INTO export_data_segments(task_id,data_generation,sequence,previous_cursor_json,last_cursor_json,row_count,configuration_hash,format_version,content_checksum,active_block_id) VALUES(#{taskId},#{dataGeneration},#{sequence},#{previousCursorJson},#{lastCursorJson},#{rowCount},#{configurationHash},#{formatVersion},#{contentChecksum},#{activeBlockId})")
  int insertSegment(SegmentRow row);

  @Update(
      "UPDATE export_checkpoints SET manifest_sealed=TRUE,manifest_version=manifest_version+1,segment_count=#{count},manifest_row_count=#{rows},manifest_digest=#{digest} WHERE task_id=#{id} AND manifest_sealed=FALSE")
  int seal(
      @Param("id") String id,
      @Param("count") int count,
      @Param("rows") long rows,
      @Param("digest") String digest);

  @Update(
      "UPDATE export_tasks SET trusted_processed_count=#{rows},execution_phase=#{phase},repair_state=#{state},version=version+1 WHERE task_id=#{id}")
  int phase(
      @Param("id") String id,
      @Param("rows") long rows,
      @Param("phase") String phase,
      @Param("state") String state);

  @Update(
      "UPDATE export_tasks SET trusted_processed_count=#{rows},execution_phase='REBUILDING',repair_state='REBUILDING',repair_epoch=repair_epoch+1,repair_attempt=repair_attempt+1,repair_reason_code=#{reason},version=version+1 WHERE task_id=#{id} AND repair_attempt<3")
  int begin(@Param("id") String id, @Param("rows") long rows, @Param("reason") String reason);

  @Insert(
      "INSERT INTO export_task_repairs(task_id,repair_epoch,attempt_no,checkpoint_version,manifest_version,reason_code,state,created_at) VALUES(#{id},#{epoch},#{attempt},#{checkpoint},#{manifest},#{reason},'REBUILDING',UTC_TIMESTAMP(3))")
  int insertRepair(
      @Param("id") String id,
      @Param("epoch") long epoch,
      @Param("attempt") int attempt,
      @Param("checkpoint") long checkpoint,
      @Param("manifest") long manifest,
      @Param("reason") String reason);

  @Insert(
      "INSERT INTO export_repair_segments(task_id,repair_epoch,sequence,old_block_id,expected_selection_version,state) VALUES(#{id},#{epoch},#{sequence},#{block},#{selection},'PENDING')")
  int planSegment(
      @Param("id") String id,
      @Param("epoch") long epoch,
      @Param("sequence") int sequence,
      @Param("block") String block,
      @Param("selection") long selection);

  @Update(
      "UPDATE export_data_segments SET state=#{state},verified_repair_epoch=#{epoch} WHERE task_id=#{id} AND data_generation=#{generation} AND sequence=#{sequence}")
  int auditSegment(
      @Param("id") String id,
      @Param("generation") long generation,
      @Param("sequence") int sequence,
      @Param("epoch") long epoch,
      @Param("state") String state);

  @Select(
      "SELECT task_id,repair_epoch,sequence,old_block_id,replacement_block_id,expected_selection_version,state FROM export_repair_segments WHERE task_id=#{id} AND repair_epoch=#{epoch} AND sequence=#{sequence} FOR UPDATE")
  ReplacementRow lockReplacement(
      @Param("id") String id, @Param("epoch") long epoch, @Param("sequence") int sequence);

  @Update(
      "UPDATE export_data_segments SET active_block_id=#{block},selection_version=selection_version+1,state='VERIFIED',verified_repair_epoch=#{epoch} WHERE task_id=#{id} AND data_generation=#{generation} AND sequence=#{sequence} AND selection_version=#{selection} AND state='REBUILD_REQUIRED'")
  int selectBlock(
      @Param("id") String id,
      @Param("generation") long generation,
      @Param("sequence") int sequence,
      @Param("selection") long selection,
      @Param("block") String block,
      @Param("epoch") long epoch);

  @Update(
      "UPDATE export_repair_segments SET replacement_block_id=#{block},state='REPLACED' WHERE task_id=#{id} AND repair_epoch=#{epoch} AND sequence=#{sequence} AND state='PENDING'")
  int replaced(
      @Param("id") String id,
      @Param("epoch") long epoch,
      @Param("sequence") int sequence,
      @Param("block") String block);

  @Update(
      "UPDATE export_tasks SET trusted_processed_count=trusted_processed_count+#{rows},processed_count=GREATEST(processed_count,trusted_processed_count),version=version+1 WHERE task_id=#{id} AND trusted_processed_count+#{rows}<=total_count")
  int addTrusted(@Param("id") String id, @Param("rows") long rows);

  @Update(
      "UPDATE export_task_repairs SET state='ASSEMBLING' WHERE task_id=#{id} AND repair_epoch=#{epoch}")
  int assembling(@Param("id") String id, @Param("epoch") long epoch);

  record SnapshotRow(
      String taskId,
      long dataGeneration,
      long checkpointVersion,
      long manifestVersion,
      long repairEpoch,
      boolean manifestSealed,
      int segmentCount,
      long manifestRowCount,
      String manifestDigest,
      String configurationHash,
      long totalCount,
      int repairAttempt,
      String segmentsJson) {}

  record SegmentRow(
      String taskId,
      long dataGeneration,
      int sequence,
      String previousCursorJson,
      String lastCursorJson,
      int rowCount,
      String configurationHash,
      int formatVersion,
      String contentChecksum,
      String activeBlockId,
      long selectionVersion,
      long verifiedRepairEpoch,
      String state) {}

  record ReplacementRow(
      String taskId,
      long repairEpoch,
      int sequence,
      String oldBlockId,
      String replacementBlockId,
      long expectedSelectionVersion,
      String state) {}
}
