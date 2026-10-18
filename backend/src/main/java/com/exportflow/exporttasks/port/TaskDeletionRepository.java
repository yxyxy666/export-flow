package com.exportflow.exporttasks.port;

import com.exportflow.exporttasks.domain.TaskDeletionModels.DeleteRequest;
import com.exportflow.exporttasks.domain.TaskDeletionModels.DeleteResult;

/** 统一删除端口；由持久化适配器裁决运行中和终态分支。 */
public interface TaskDeletionRepository {
  DeleteResult delete(DeleteRequest request);
}
