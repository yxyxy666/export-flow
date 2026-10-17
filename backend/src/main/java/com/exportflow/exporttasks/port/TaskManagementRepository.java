// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[任务接口](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.exporttasks.port;

import com.exportflow.exporttasks.domain.TaskManagementModels.*;
import java.util.UUID;

public interface TaskManagementRepository {
  ResumeResult resume(ResumeRequest request);

  TaskPage findPage(TaskQuery query);

  TaskListItem find(UUID taskId);

  PauseResult pause(PauseRequest request);
}
