// 设计：[可靠进度通知](../../../../../../../../specs/004-task-management/data-model.md)。
package com.exportflow.progress.domain;

import java.util.UUID;

public final class ProgressModels {
  private ProgressModels() {}

  public record ProgressNotice(
      int schemaVersion, UUID taskId, long version, long activityVersion) {}

  public record NoticeLease(
      UUID taskId, long pendingVersion, long activityVersion, String owner, UUID token) {}

  public record PublishProof(boolean confirmAck, boolean correctRoute) {
    public boolean reliable() {
      return confirmAck && correctRoute;
    }
  }

  public record IdleCandidate(long activityVersion, boolean hasActiveTasks) {}
}
