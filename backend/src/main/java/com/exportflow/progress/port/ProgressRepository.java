// 设计：[可靠进度通知](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.progress.port;

import com.exportflow.progress.domain.ProgressModels.*;
import java.util.List;
import java.util.UUID;

public interface ProgressRepository {
  void lockActivity();

  void recordChange(UUID taskId, long version);

  List<NoticeLease> claimPending(String owner, int limit);

  boolean markPublished(NoticeLease lease, PublishProof proof);

  void release(NoticeLease lease);

  IdleCandidate prepareIdle();

  boolean confirmIdle(IdleCandidate candidate);
}
