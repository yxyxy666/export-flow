package com.exportflow.exporttasks.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 统一删除的锁、状态转换和业务记录清理SQL。 */
@Mapper
public interface TaskDeletionMapper {
  @Select(
      "SELECT task_id,status,version,control_version,deletion_state FROM export_tasks WHERE task_id=#{id} FOR UPDATE")
  TaskRow lockTask(@Param("id") String id);

  @Select(
      "SELECT operation_id,task_id,request_hash,expected_version,mode,state,prior_status,result_json FROM task_deletion_operations WHERE operation_id=#{id} FOR UPDATE")
  OperationRow lockOperation(@Param("id") String id);

  @Insert(
      "INSERT INTO task_deletion_operations(operation_id,task_id,request_hash,expected_version,mode,prior_status,state,result_json,created_at,updated_at) VALUES(#{operation},#{task},#{hash},#{expected},#{mode},#{priorStatus},#{state},#{result},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
  int insertOperation(
      @Param("operation") String operation,
      @Param("task") String task,
      @Param("hash") byte[] hash,
      @Param("expected") long expected,
      @Param("mode") String mode,
      @Param("priorStatus") String priorStatus,
      @Param("state") String state,
      @Param("result") String result);

  @Update(
      "UPDATE export_tasks SET deletion_state=#{state},deletion_mode=#{mode},deletion_operation_id=#{operation},deletion_requested_at=UTC_TIMESTAMP(3),control_version=control_version+1,version=version+1 WHERE task_id=#{task} AND version=#{expected} AND deletion_state='NONE'")
  int requestDelete(
      @Param("task") String task,
      @Param("expected") long expected,
      @Param("operation") String operation,
      @Param("mode") String mode,
      @Param("state") String state);

  /** 删除屏障建立后，尚未执行的命令立即失效；已发送消息由墓碑裁决。 */
  @Update(
      "UPDATE command_outbox SET status='CLOSED',closed_at=UTC_TIMESTAMP(3),close_reason='TASK_DELETING' WHERE task_id=#{task} AND status IN ('READY','LEASED','SENT')")
  int closeCommands(@Param("task") String task);

  @Update(
      "UPDATE task_deletion_operations SET state=#{state},result_json=#{result},updated_at=UTC_TIMESTAMP(3) WHERE operation_id=#{operation}")
  int updateOperation(
      @Param("operation") String operation,
      @Param("state") String state,
      @Param("result") String result);

  @Update("UPDATE task_activity SET version=version+1 WHERE id=1")
  int incrementActivity();

  @Insert(
      "INSERT INTO task_deletion_jobs(job_id,task_id,operation_id,phase,attempt_no,next_attempt_at,created_at,updated_at) VALUES(#{job},#{task},#{operation},#{phase},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))")
  int insertJob(
      @Param("job") String job,
      @Param("task") String task,
      @Param("operation") String operation,
      @Param("phase") String phase);

  @Update(
      "UPDATE task_deletion_jobs SET phase=#{phase},attempt_no=attempt_no+1,last_error_code=#{error},lease_owner=NULL,lease_until=NULL,next_attempt_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE job_id=#{job}")
  int updateJob(
      @Param("job") String job, @Param("phase") String phase, @Param("error") String error);

  @Select(
      "SELECT job_id,task_id,operation_id,phase,attempt_no,lease_owner,lease_until FROM task_deletion_jobs WHERE phase IN ('STOP_WAIT','DB_CLEANUP','FILE_CLEANUP','RETRYABLE_FAILURE') AND next_attempt_at<=UTC_TIMESTAMP(3) AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(3)) ORDER BY created_at,job_id LIMIT 1 FOR UPDATE")
  JobRow lockDueJob();

  @Update(
      "UPDATE task_deletion_jobs SET lease_owner=#{owner},lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND),updated_at=UTC_TIMESTAMP(3) WHERE job_id=#{job}")
  int claimJob(@Param("job") String job, @Param("owner") String owner);

  @Update(
      "UPDATE task_deletion_jobs SET phase=#{phase},attempt_no=attempt_no+1,last_error_code=#{error},lease_owner=NULL,lease_until=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL #{delaySeconds} SECOND),updated_at=UTC_TIMESTAMP(3) WHERE job_id=#{job} AND lease_owner=#{owner}")
  int retryJob(
      @Param("job") String job,
      @Param("phase") String phase,
      @Param("error") String error,
      @Param("delaySeconds") long delaySeconds,
      @Param("owner") String owner);

  @Update(
      "UPDATE task_deletion_jobs SET phase='DONE',lease_owner=NULL,lease_until=NULL,last_error_code=NULL,updated_at=UTC_TIMESTAMP(3) WHERE job_id=#{job} AND lease_owner=#{owner}")
  int completeJob(@Param("job") String job, @Param("owner") String owner);

  @Select("SELECT event_id FROM command_outbox WHERE task_id=#{task}")
  List<String> commandIds(@Param("task") String task);

  @Delete("DELETE FROM dispatch_attempts WHERE event_id=#{event}")
  int deleteDispatchAttempts(@Param("event") String event);

  @Delete("DELETE FROM export_task_orders WHERE task_id=#{task}")
  int deleteTaskOrders(@Param("task") String task);

  @Delete("DELETE FROM export_idempotency WHERE task_id=#{task}")
  int deleteIdempotency(@Param("task") String task);

  @Delete("DELETE FROM task_pause_operations WHERE task_id=#{task}")
  int deletePauseOperations(@Param("task") String task);

  @Delete("DELETE FROM task_resume_operations WHERE task_id=#{task}")
  int deleteResumeOperations(@Param("task") String task);

  @Delete("DELETE FROM task_progress_outbox WHERE task_id=#{task}")
  int deleteProgressOutbox(@Param("task") String task);

  @Delete("DELETE FROM export_repair_segments WHERE task_id=#{task}")
  int deleteRepairSegments(@Param("task") String task);

  @Delete("DELETE FROM export_task_repairs WHERE task_id=#{task}")
  int deleteRepairs(@Param("task") String task);

  @Delete("DELETE FROM export_data_segments WHERE task_id=#{task}")
  int deleteDataSegments(@Param("task") String task);

  @Delete("DELETE FROM export_data_blocks WHERE task_id=#{task}")
  int deleteDataBlocks(@Param("task") String task);

  @Delete("DELETE FROM export_checkpoints WHERE task_id=#{task}")
  int deleteCheckpoints(@Param("task") String task);

  @Delete("DELETE FROM export_cleanup_candidates WHERE task_id=#{task}")
  int deleteCleanupCandidates(@Param("task") String task);

  @Update("UPDATE export_tasks SET result_artifact_id=NULL WHERE task_id=#{task}")
  int detachResultArtifact(@Param("task") String task);

  @Delete("DELETE FROM export_artifacts WHERE task_id=#{task}")
  int deleteArtifacts(@Param("task") String task);

  @Delete("DELETE FROM task_execution WHERE task_id=#{task}")
  int deleteExecution(@Param("task") String task);

  @Delete("DELETE FROM command_outbox WHERE task_id=#{task}")
  int deleteCommands(@Param("task") String task);

  @Delete("DELETE FROM export_tasks WHERE task_id=#{task}")
  int deleteTask(@Param("task") String task);

  @Insert(
      "INSERT INTO task_deletion_tombstones(task_id,task_no_hash,operation_id,deleted_at,expires_at,state) VALUES(#{task},#{taskHash},#{operation},UTC_TIMESTAMP(3),NULL,'ACTIVE') ON DUPLICATE KEY UPDATE operation_id=VALUES(operation_id),deleted_at=VALUES(deleted_at),state='ACTIVE'")
  int upsertTombstone(
      @Param("task") String task,
      @Param("taskHash") String taskHash,
      @Param("operation") String operation);

  @Select(
      "SELECT task_id,operation_id,mode,prior_status,state,result_json FROM task_deletion_operations WHERE operation_id=#{id}")
  OperationRow findOperation(@Param("id") String id);

  record TaskRow(String taskId, String status, long version, long controlVersion, String deletionState) {}

  record OperationRow(
      String operationId,
      String taskId,
      byte[] requestHash,
      long expectedVersion,
      String mode,
      String priorStatus,
      String state,
      String resultJson) {}

  record JobRow(
      String jobId,
      String taskId,
      String operationId,
      String phase,
      int attemptNo,
      String leaseOwner,
      LocalDateTime leaseUntil) {}
}
