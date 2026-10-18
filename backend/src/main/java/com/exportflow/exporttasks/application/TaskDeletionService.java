package com.exportflow.exporttasks.application;

import com.exportflow.exporttasks.domain.TaskDeletionModels.DeleteRequest;
import com.exportflow.exporttasks.domain.TaskDeletionModels.DeleteResult;
import com.exportflow.exporttasks.port.TaskDeletionRepository;
import com.exportflow.shared.domain.BusinessException;
import org.springframework.stereotype.Service;

/** 统一删除用例；服务层不按状态暴露多个接口。 */
@Service
public class TaskDeletionService {
  private final TaskDeletionRepository repository;

  public TaskDeletionService(TaskDeletionRepository repository) {
    this.repository = repository;
  }

  public DeleteResult delete(DeleteRequest request) {
    try {
      return repository.delete(request);
    } catch (BusinessException known) {
      throw known;
    } catch (RuntimeException unavailable) {
      throw new BusinessException("STATE_UNAVAILABLE", "任务删除状态暂不可用", 503, "UNKNOWN", null);
    }
  }
}
