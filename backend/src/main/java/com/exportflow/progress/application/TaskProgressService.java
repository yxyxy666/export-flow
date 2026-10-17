// 设计：[通知发送与消费](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.progress.application;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.port.TaskManagementRepository;
import com.exportflow.progress.domain.ProgressModels.*;
import com.exportflow.progress.port.*;
import org.springframework.stereotype.Service;

@Service
public class TaskProgressService {
  private final ProgressRepository progress;
  private final ProgressCache cache;
  private final ProgressPublisher publisher;
  private final TaskManagementRepository tasks;
  private final SseConnectionRegistry connections;
  private final String owner;

  public TaskProgressService(
      ProgressRepository progress,
      ProgressCache cache,
      ProgressPublisher publisher,
      TaskManagementRepository tasks,
      SseConnectionRegistry connections,
      RuntimeProperties properties) {
    this.progress = progress;
    this.cache = cache;
    this.publisher = publisher;
    this.tasks = tasks;
    this.connections = connections;
    owner = properties.getInstanceId();
  }

  public record DispatchSummary(int claimed, int published, int retained) {}

  /** I/O在领取事务之后；只可靠confirm才推进发布版本。 */
  public DispatchSummary dispatchPending(int limit) {
    var leases = progress.claimPending(owner, limit);
    int published = 0;
    for (var lease : leases) {
      try {
        var task = tasks.find(lease.taskId());
        if (task == null || task.version() < lease.pendingVersion()) {
          progress.release(lease);
          continue;
        }
        try {
          cache.putIfNewer(task);
        } catch (RuntimeException unavailable) {
          /* 可选缓存不阻断可靠发布。 */
        }
        var proof =
            publisher.publish(
                new ProgressNotice(
                    1, task.taskId(), lease.pendingVersion(), lease.activityVersion()));
        if (progress.markPublished(lease, proof)) published++;
        else progress.release(lease);
      } catch (RuntimeException unavailable) {
        progress.release(lease);
      }
    }
    return new DispatchSummary(leases.size(), published, leases.size() - published);
  }

  /** 本地发送完成才交还ACK裁决；终态始终权威回读。 */
  public boolean onNotice(ProgressNotice notice) {
    try {
      if (notice.schemaVersion() != 1
          || notice.taskId() == null
          || notice.version() < 0
          || notice.activityVersion() < 0) return false;
      com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem task = null;
      try {
        task = cache.get(notice.taskId(), notice.version()).orElse(null);
      } catch (RuntimeException unavailable) {
        /* 回源。 */
      }
      if (task == null
          || task.status() == TaskStatus.SUCCEEDED
          || task.status() == TaskStatus.FAILED) task = tasks.find(notice.taskId());
      if (task == null || task.version() < notice.version()) return false;
      return connections
          .broadcast(notice.activityVersion(), task)
          .toCompletableFuture()
          .get(10, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception unavailable) {
      if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
      return false;
    }
  }
}
