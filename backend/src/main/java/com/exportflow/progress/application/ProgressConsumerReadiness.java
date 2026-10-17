// 设计：[实例身份与可靠拓扑](../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.application;

import com.exportflow.config.RuntimeProperties;
import java.util.concurrent.*;
import org.springframework.amqp.rabbit.listener.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 只有broker确认exclusive consumer后才就绪，同稳定ID重复启动必须失败。 */
@Component
public class ProgressConsumerReadiness {
  private final RuntimeProperties properties;
  private final CompletableFuture<Void> subscribed = new CompletableFuture<>();

  public ProgressConsumerReadiness(RuntimeProperties properties) {
    this.properties = properties;
  }

  @EventListener
  public void consumed(ConsumeOkEvent event) {
    if (event.getQueue().equals("exportflow.progress.v1." + properties.getInstanceId()))
      subscribed.complete(null);
  }

  @EventListener
  public void failed(ListenerContainerConsumerFailedEvent event) {
    if (event.getSource() instanceof AbstractMessageListenerContainer container
        && "task-progress".equals(container.getListenerId()))
      subscribed.completeExceptionally(new IllegalStateException("进度实例订阅不可用，请检查实例标识是否重复及消息服务"));
  }

  @EventListener(ApplicationReadyEvent.class)
  public void verify() {
    if (!properties.isProgressEnabled()) return;
    try {
      subscribed.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("进度订阅启动中断");
    } catch (ExecutionException | TimeoutException unavailable) {
      throw new IllegalStateException("进度实例订阅不可用，请检查实例标识是否重复及消息服务");
    }
  }

  public boolean isReady() {
    return properties.isProgressEnabled()
        && subscribed.isDone()
        && !subscribed.isCompletedExceptionally();
  }
}
