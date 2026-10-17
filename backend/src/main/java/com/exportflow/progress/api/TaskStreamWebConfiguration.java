// 设计：[发送完成边界](../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.api;

import com.exportflow.progress.application.SseConnectionRegistry;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 回调发生于MVC安装SSE Handler之后，将实际发送边界交给注册表。 */
@Configuration
public class TaskStreamWebConfiguration implements WebMvcConfigurer {
  private final SseConnectionRegistry connections;

  public TaskStreamWebConfiguration(SseConnectionRegistry connections) {
    this.connections = connections;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new AsyncHandlerInterceptor() {
              @Override
              public void afterConcurrentHandlingStarted(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (request.getAttribute(SseConnectionRegistry.REQUEST_EMITTER)
                    instanceof SseEmitter emitter) connections.activate(emitter);
              }
            })
        .addPathPatterns("/api/v1/export-tasks/events");
  }
}
