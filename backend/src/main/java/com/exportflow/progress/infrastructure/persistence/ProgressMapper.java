// 设计：[可靠进度事务](../../../../../../../../../specs/004-task-management/data-model.md)。
package com.exportflow.progress.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProgressMapper {
  @Select("SELECT version FROM task_activity WHERE id=1 FOR UPDATE")
  long lockActivity();

  @Update("UPDATE task_activity SET version=version+1 WHERE id=1")
  int advanceActivity();

  @Select("SELECT version FROM task_activity WHERE id=1")
  long activity();

  @Insert(
      "INSERT INTO task_progress_outbox(task_id,pending_version,activity_version) VALUES(#{taskId},#{version},#{activity}) ON DUPLICATE KEY UPDATE pending_version=GREATEST(pending_version,#{version}),activity_version=GREATEST(activity_version,#{activity})")
  int record(
      @Param("taskId") String taskId,
      @Param("version") long version,
      @Param("activity") long activity);

  @Select(
      "SELECT task_id FROM task_progress_outbox WHERE pending_version>published_version AND next_attempt_at<=UTC_TIMESTAMP(3) AND (lease_until IS NULL OR lease_until<=UTC_TIMESTAMP(3)) ORDER BY task_id LIMIT #{limit}")
  List<String> due(int limit);

  @Select(
      "SELECT task_id,pending_version,activity_version,lease_owner,lease_token FROM task_progress_outbox WHERE task_id=#{id} FOR UPDATE")
  NoticeRecord lock(String id);

  @Update(
      "UPDATE task_progress_outbox SET lease_owner=#{owner},lease_token=#{token},lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND) WHERE task_id=#{id} AND pending_version>published_version AND (lease_until IS NULL OR lease_until<=UTC_TIMESTAMP(3))")
  int claim(@Param("id") String id, @Param("owner") String owner, @Param("token") String token);

  @Update(
      "UPDATE task_progress_outbox SET published_version=GREATEST(published_version,#{version}),lease_owner=NULL,lease_token=NULL,lease_until=NULL WHERE task_id=#{id} AND lease_token=#{token} AND lease_owner=#{owner}")
  int published(
      @Param("id") String id,
      @Param("token") String token,
      @Param("owner") String owner,
      @Param("version") long version);

  @Update(
      "UPDATE task_progress_outbox SET lease_owner=NULL,lease_token=NULL,lease_until=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE task_id=#{id} AND lease_token=#{token}")
  int release(@Param("id") String id, @Param("token") String token);

  @Select(
      "SELECT a.version AS activity_version,EXISTS(SELECT 1 FROM export_tasks WHERE status IN ('PENDING','RUNNING','PAUSING')) AS has_active_tasks FROM task_activity a WHERE id=1")
  IdleRecord idle();

  record NoticeRecord(
      String taskId,
      long pendingVersion,
      long activityVersion,
      String leaseOwner,
      String leaseToken) {}

  record IdleRecord(long activityVersion, boolean hasActiveTasks) {}
}
