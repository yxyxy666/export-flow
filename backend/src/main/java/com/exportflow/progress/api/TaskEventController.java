// 设计：[SSE端点](../../../../../../../../specs/004-task-management/contracts/http-sse.md)。
package com.exportflow.progress.api;

import com.exportflow.progress.application.SseConnectionRegistry;
import com.exportflow.shared.domain.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class TaskEventController {
  private final SseConnectionRegistry connections;
  private final com.exportflow.progress.application.ProgressConsumerReadiness readiness;

  public TaskEventController(
      SseConnectionRegistry connections,
      com.exportflow.progress.application.ProgressConsumerReadiness readiness) {
    this.connections = connections;
    this.readiness = readiness;
  }

  @GetMapping(path = "/api/v1/export-tasks/events", produces = "text/event-stream")
  public SseEmitter events(
      jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
    if (!readiness.isReady())
      throw new BusinessException("STATE_UNAVAILABLE", "任务进度暂不可用", 503, "UNKNOWN", null);
    response.setHeader("Cache-Control", "no-cache, no-transform");
    response.setHeader("X-Accel-Buffering", "no");
    try {
      var emitter = connections.open();
      request.setAttribute(SseConnectionRegistry.REQUEST_EMITTER, emitter);
      return emitter;
    } catch (RuntimeException unavailable) {
      throw new BusinessException("STATE_UNAVAILABLE", "任务状态暂不可用", 503, "UNKNOWN", null);
    }
  }
}
