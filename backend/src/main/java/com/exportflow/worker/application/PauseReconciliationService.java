// 设计：[崩溃后的暂停对账](../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.worker.application;

import com.exportflow.exporttasks.infrastructure.persistence.TaskManagementMapper;
import com.exportflow.worker.port.TaskExecutionRepository;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PauseReconciliationService {
  private final TaskExecutionRepository executions;
  private final TaskManagementMapper tasks;

  public PauseReconciliationService(
      TaskExecutionRepository executions, TaskManagementMapper tasks) {
    this.executions = executions;
    this.tasks = tasks;
  }

  /** 只裁决停止与隔离，不认领、不调用生成器，也不创建第二条命令。 */
  @Scheduled(fixedDelay = 1000)
  public void scanPausedRequests() {
    try {
      for (var id : tasks.pausing()) executions.reconcilePause(UUID.fromString(id));
    } catch (RuntimeException unknown) {
      /* 依赖不明保留PAUSING，下次重新读权威状态。 */
    }
  }
}
