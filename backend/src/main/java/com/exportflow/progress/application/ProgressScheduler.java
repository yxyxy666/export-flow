// 设计：[独立进度调度](../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.progress.application;

import com.exportflow.config.RuntimeProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProgressScheduler {
  private final TaskProgressService service;
  private final SseConnectionRegistry connections;
  private final RuntimeProperties properties;

  public ProgressScheduler(
      TaskProgressService service,
      SseConnectionRegistry connections,
      RuntimeProperties properties) {
    this.service = service;
    this.connections = connections;
    this.properties = properties;
  }

  @Scheduled(fixedDelay = 1000)
  public void dispatch() {
    if (properties.isProgressEnabled())
      try {
        service.dispatchPending(20);
      } catch (RuntimeException unavailable) {
        /* 保留持久待发状态。 */
      }
  }

  @Scheduled(fixedDelay = 1000)
  public void checkIdle() {
    try {
      connections.checkIdle();
    } catch (RuntimeException unavailable) {
      /* 不可读时不能宣告空闲。 */
    }
  }

  @Scheduled(fixedDelay = 15000)
  public void heartbeat() {
    connections.heartbeat();
  }
}
