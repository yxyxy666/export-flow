// 设计：[可靠通知](../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.port;

import com.exportflow.progress.domain.ProgressModels.*;

public interface ProgressPublisher {
  PublishProof publish(ProgressNotice notice);
}
