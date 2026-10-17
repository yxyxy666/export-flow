// 设计：[列表单语句与暂停事务](../../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.exporttasks.infrastructure.persistence;

import com.exportflow.exporttasks.domain.TaskManagementModels.TaskQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskManagementMapper {
  List<ListRow> findPage(TaskQuery query);

  ListRow find(String taskId);

  @Select(
      "SELECT operation_id,task_id,request_hash,expected_control_version FROM task_pause_operations WHERE operation_id=#{id}")
  OperationRow operation(String id);

  @Insert(
      "INSERT INTO task_pause_operations(operation_id,task_id,request_hash,expected_control_version,result_json,created_at) VALUES(#{operation},#{task},#{hash},#{expected},#{result},UTC_TIMESTAMP(3))")
  int insertOperation(
      @Param("operation") String operation,
      @Param("task") String task,
      @Param("hash") byte[] hash,
      @Param("expected") long expected,
      @Param("result") String result);

  @Update(
      "UPDATE export_tasks SET control_version=control_version+1,version=version+1,pause_requested_at=UTC_TIMESTAMP(3),paused_at=IF(status='PENDING',UTC_TIMESTAMP(3),NULL),repair_state=IF(repair_state IN ('CHECKING','REBUILDING'),IF(status='PENDING','PAUSED','PAUSING'),repair_state),status=IF(status='PENDING','PAUSED','PAUSING') WHERE task_id=#{id} AND status IN ('PENDING','RUNNING') AND control_version=#{expected}")
  int pause(@Param("id") String id, @Param("expected") long expected);

  @Update(
      "UPDATE export_tasks SET status='PAUSED',paused_at=UTC_TIMESTAMP(3),version=version+1,repair_state=IF(repair_state='PAUSING','PAUSED',repair_state) WHERE task_id=#{id} AND status='PAUSING'")
  int paused(String id);

  @Update(
      "UPDATE task_execution SET pause_ack_version=#{version},fence=fence+1,lease_until=UTC_TIMESTAMP(3),heartbeat_at=UTC_TIMESTAMP(3) WHERE task_id=#{id}")
  int stopExecution(@Param("id") String id, @Param("version") long version);

  @Select("SELECT task_id FROM export_tasks WHERE status='PAUSING' ORDER BY task_id LIMIT 100")
  List<String> pausing();

  record OperationRow(
      String operationId, String taskId, byte[] requestHash, long expectedControlVersion) {}

  record ListRow(
      String taskId,
      String taskNo,
      String scopeType,
      long totalCount,
      long trustedProcessedCount,
      String status,
      String fieldsJson,
      String fileName,
      Long fileSize,
      LocalDateTime createdAt,
      LocalDateTime finishedAt,
      String errorCode,
      String errorSummary,
      long version,
      long controlVersion,
      String executionPhase,
      String repairState,
      long repairEpoch,
      int repairAttempt,
      String repairReasonCode,
      long total,
      long activityVersion,
      boolean hasActiveTasks) {}
}
