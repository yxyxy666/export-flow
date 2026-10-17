// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
// 职责：Outbox 持久记录与 SQL 映射。
package com.exportflow.dispatch.infrastructure.persistence;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** 以数据库时间创建受理窗口；设计 data-model.md，验证 TC-218/222。 */
@Mapper
public interface CommandOutboxMapper {
  @Select(
      "SELECT EXISTS(SELECT 1 FROM command_outbox WHERE task_id=#{taskId} AND status='CLOSED' AND close_reason='USER_PAUSED')")
  boolean pausedSealed(String taskId);

  @Select("SELECT UTC_TIMESTAMP(3)")
  Instant databaseNow();

  @Insert(
      "INSERT INTO command_outbox(event_id,task_id,type,schema_version,status,accepted_at,dispatch_deadline,next_attempt_at,attempt_no) VALUES(#{eventId},#{taskId},'EXPORT_REQUESTED',1,'READY',#{acceptedAt},TIMESTAMPADD(MINUTE,10,#{acceptedAt}),#{acceptedAt},0)")
  int insert(
      @Param("taskId") String taskId,
      @Param("eventId") String eventId,
      @Param("acceptedAt") Instant acceptedAt);

  List<String> findDue(@Param("limit") int limit);

  CommandOutboxRecord lockByTask(@Param("taskId") String taskId);

  Optional<CommandOutboxRecord> find(@Param("eventId") String eventId);

  int reserveAttempt(
      @Param("eventId") String eventId,
      @Param("owner") String owner,
      @Param("token") String token,
      @Param("attempt") int attempt,
      @Param("leaseUntil") Instant leaseUntil,
      @Param("nextAt") Instant nextAt);

  int insertAttempt(
      @Param("eventId") String eventId,
      @Param("attempt") int attempt,
      @Param("token") String token,
      @Param("now") Instant now,
      @Param("nextAt") Instant nextAt);

  int markSent(
      @Param("eventId") String eventId, @Param("token") String token, @Param("now") Instant now);

  int scheduleRetry(
      @Param("eventId") String eventId,
      @Param("token") String token,
      @Param("safeCode") String safeCode,
      @Param("nextAt") Instant nextAt);

  int finishAttempt(
      @Param("eventId") String eventId,
      @Param("attempt") int attempt,
      @Param("token") String token,
      @Param("outcome") String outcome,
      @Param("safeCode") String safeCode,
      @Param("now") Instant now);

  List<String> findExpired(@Param("limit") int limit);

  int close(
      @Param("eventId") String eventId, @Param("reason") String reason, @Param("now") Instant now);
}
