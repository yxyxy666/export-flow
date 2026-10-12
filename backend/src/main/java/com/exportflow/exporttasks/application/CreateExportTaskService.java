// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
// 职责：创建校验、幂等与同事务保存任务及 Outbox。
package com.exportflow.exporttasks.application;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.port.*;
import com.exportflow.shared.domain.BusinessException;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 唯一冲突/提交异常结束原事务后核对；设计 contracts/http-api.md，验证 TC-217/218。 */
@Service
public class CreateExportTaskService {
  private final ExportRequestCanonicalizer canonicalizer;
  private final CreateExportTransaction creator;
  private final IdempotencyRepository keys;
  private final ExportTaskRepository tasks;
  private final TransactionTemplate reader;

  public CreateExportTaskService(
      ExportRequestCanonicalizer canonicalizer,
      CreateExportTransaction creator,
      IdempotencyRepository keys,
      ExportTaskRepository tasks,
      PlatformTransactionManager manager) {
    this.canonicalizer = canonicalizer;
    this.creator = creator;
    this.keys = keys;
    this.tasks = tasks;
    reader = new TransactionTemplate(manager);
    reader.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    reader.setReadOnly(true);
  }

  public CreateTaskResult create(CreateExportCommand command) {
    var submission = canonicalizer.normalize(command);
    try {
      return creator.commit(submission);
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      return reconcile(submission);
    }
  }

  public CreateTaskResult reconcile(CanonicalSubmission submission) {
    try {
      return reader.execute(
          status -> {
            var saved =
                keys.findByKeyHash(submission.keyHash())
                    .orElseThrow(CreateExportTaskService::unknown);
            if (saved.canonicalVersion() != submission.version()
                || !MessageDigest.isEqual(saved.requestHash(), submission.requestHash()))
              throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "提交标识与请求不一致，请重新配置", 409);
            return new CreateTaskResult(
                tasks.findSummary(saved.taskId()).orElseThrow(CreateExportTaskService::unknown),
                true);
          });
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw unknown();
    }
  }

  private static BusinessException unknown() {
    return new BusinessException(
        "CREATE_OUTCOME_UNKNOWN", "创建结果尚未确认，请用原请求重试确认", 503, "UNKNOWN", null);
  }
}
