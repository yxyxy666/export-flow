// 设计：[可降级缓存](../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.port;

import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import java.util.*;

public interface ProgressCache {
  Optional<TaskListItem> get(UUID taskId, long minimumVersion);

  void putIfNewer(TaskListItem task);
}
