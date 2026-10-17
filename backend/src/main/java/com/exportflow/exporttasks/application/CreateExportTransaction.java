// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
package com.exportflow.exporttasks.application;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.dispatch.infrastructure.persistence.CommandOutboxMapper;
import com.exportflow.dispatch.port.CommandOutboxRepository;
import com.exportflow.exporttasks.domain.ExportTask;
import com.exportflow.exporttasks.port.*;
import com.exportflow.shared.domain.BusinessException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 四表原子写入，execute返回才确认提交；设计 function-design-reference.md，验证 TC-217/218。 */
@Service
public class CreateExportTransaction {
  private final TransactionTemplate transaction;
  private final ExportTaskRepository tasks;
  private final IdempotencyRepository keys;
  private final CommandOutboxRepository outbox;
  private final ExportRangeService ranges;
  private final CommandOutboxMapper clock;
  private final com.exportflow.progress.port.ProgressRepository progress;

  public CreateExportTransaction(
      PlatformTransactionManager manager,
      ExportTaskRepository tasks,
      IdempotencyRepository keys,
      CommandOutboxRepository outbox,
      ExportRangeService ranges,
      CommandOutboxMapper clock,
      com.exportflow.progress.port.ProgressRepository progress) {
    transaction = new TransactionTemplate(manager);
    this.tasks = tasks;
    this.keys = keys;
    this.outbox = outbox;
    this.ranges = ranges;
    this.clock = clock;
    this.progress = progress;
  }

  public CreateTaskResult commit(CanonicalSubmission submission) {
    return Objects.requireNonNull(
        transaction.execute(
            status -> {
              progress.lockActivity();
              var existing = keys.findByKeyHash(submission.keyHash());
              if (existing.isPresent()) {
                if (existing.get().canonicalVersion() != submission.version()
                    || !java.security.MessageDigest.isEqual(
                        existing.get().requestHash(), submission.requestHash()))
                  throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "提交标识与请求不一致，请重新配置", 409);
                return new CreateTaskResult(
                    tasks.findSummary(existing.get().taskId()).orElseThrow(), true);
              }
              var range = ranges.verify(submission.scope());
              Instant now = clock.databaseNow().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
              var identity = newTaskIdentity(now);
              var task =
                  ExportTask.pending(
                      identity.taskId(),
                      identity.taskNo(),
                      submission.scope(),
                      submission.fields(),
                      range.total(),
                      now);
              tasks.insert(task, submission.scope(), submission.fields());
              keys.insert(submission, task.taskId());
              outbox.insert(task.taskId(), identity.eventId(), now);
              progress.recordChange(task.taskId(), task.version());
              return new CreateTaskResult(tasks.findSummary(task.taskId()).orElseThrow(), false);
            }));
  }

  public TaskIdentity newTaskIdentity(Instant acceptedAt) {
    UUID taskId = UUID.randomUUID();
    String date =
        DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("Asia/Hong_Kong"))
            .format(acceptedAt);
    return new TaskIdentity(
        taskId,
        UUID.randomUUID(),
        "EXP" + date + "-" + taskId.toString().replace("-", "").toUpperCase(Locale.ROOT));
  }
}
