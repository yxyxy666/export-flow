// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[任务管理用例](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.exporttasks.application;

import com.exportflow.exporttasks.domain.TaskManagementModels.*;
import com.exportflow.exporttasks.port.TaskManagementRepository;
import com.exportflow.shared.domain.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class TaskManagementService {
  private final TaskManagementRepository tasks;

  public TaskManagementService(TaskManagementRepository tasks) {
    this.tasks = tasks;
  }

  public TaskPage query(TaskQuery query) {
    try {
      return tasks.findPage(query);
    } catch (BusinessException known) {
      throw known;
    } catch (RuntimeException unavailable) {
      throw unavailable();
    }
  }

  public PauseResult pause(PauseRequest request) {
    try {
      return tasks.pause(request);
    } catch (BusinessException known) {
      throw known;
    } catch (RuntimeException unknown) {
      throw unavailable();
    }
  }

  private BusinessException unavailable() {
    return new BusinessException("STATE_UNAVAILABLE", "任务状态暂不可用", 503, "UNKNOWN", null);
  }

  public ResumeResult resume(ResumeRequest request) {
    try {
      return tasks.resume(request);
    } catch (BusinessException known) {
      throw known;
    } catch (RuntimeException unknown) {
      throw unavailable();
    }
  }
}
