// 设计：[单连接串行发送与空闲裁决](../../../../../../../../specs/004-task-management/contracts/http-sse.md)。
package com.exportflow.progress.application;

import com.exportflow.exporttasks.api.TaskManagementApiMapper;
import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import com.exportflow.progress.domain.ProgressModels.IdleCandidate;
import com.exportflow.progress.port.ProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseConnectionRegistry {
  private final ProgressRepository progress;
  private final TaskManagementApiMapper mapper;
  private final ObjectMapper json;
  private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
  private final Object registration = new Object();
  private final java.util.function.Supplier<SseEmitter> emitters;
  private final int capacity;
  public static final String REQUEST_EMITTER = SseConnectionRegistry.class.getName() + ".emitter";

  @Autowired
  public SseConnectionRegistry(
      ProgressRepository progress, TaskManagementApiMapper mapper, ObjectMapper json) {
    this(progress, mapper, json, () -> new SseEmitter(0L), 256);
  }

  SseConnectionRegistry(
      ProgressRepository progress,
      TaskManagementApiMapper mapper,
      ObjectMapper json,
      java.util.function.Supplier<SseEmitter> emitters,
      int capacity) {
    this.progress = progress;
    this.mapper = mapper;
    this.json = json;
    this.emitters = emitters;
    this.capacity = capacity;
  }

  /** MVC返回值处理器安装真实Handler后才允许发送，避免提前缓冲被误判为已发送。 */
  public void activate(SseEmitter emitter) {
    for (var connection : connections.values())
      if (connection.emitter == emitter) connection.initialized.complete(null);
  }

  /** 注册与ready入队同一临界区，后续更新排在ready之后；不持有DB事务。 */
  public SseEmitter open() {
    var activity = progress.prepareIdle();
    var connection = new Connection();
    connection.emitter.onCompletion(() -> close(connection.id));
    connection.emitter.onError(error -> close(connection.id));
    synchronized (registration) {
      connections.put(connection.id, connection);
      connection.activity = activity.activityVersion();
      connection.send(
          SseEmitter.event()
              .name("stream-ready")
              .id(connection.id + "/" + activity.activityVersion())
              .data(
                  encode(
                      Map.of(
                          "connectionId",
                          connection.id,
                          "activityVersion",
                          Long.toString(activity.activityVersion())))));
    }
    return connection.emitter;
  }

  public CompletionStage<Boolean> broadcast(long activity, TaskListItem task) {
    List<CompletableFuture<Boolean>> sends = new ArrayList<>();
    synchronized (registration) {
      for (var connection : connections.values())
        synchronized (connection) {
          if (connection.draining) continue;
          connection.activity = Math.max(connection.activity, activity);
          connection.candidate = null;
          sends.add(
              connection.send(
                  SseEmitter.event()
                      .name("task-updated")
                      .id(
                          connection.id
                              + "/"
                              + activity
                              + "/"
                              + task.taskId()
                              + "/"
                              + task.version())
                      .data(
                          encode(
                              Map.of(
                                  "activityVersion",
                                  Long.toString(activity),
                                  "task",
                                  mapper.toDto(task))))));
        }
    }
    return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> sends.stream().allMatch(CompletableFuture::join));
  }

  /** 两次权威读与活动锁裁决；一秒宽限仅为运行参数，本期不测时间。 */
  public void checkIdle() {
    if (connections.isEmpty()) return;
    var candidate = progress.prepareIdle();
    long now = System.nanoTime();
    for (var connection : connections.values())
      synchronized (connection) {
        if (connection.draining) continue;
        if (candidate.hasActiveTasks() || candidate.activityVersion() < connection.activity) {
          connection.candidate = null;
          continue;
        }
        if (connection.candidate == null
            || connection.candidate.activityVersion() != candidate.activityVersion()) {
          connection.candidate = candidate;
          connection.candidateAt = now;
          continue;
        }
        if (now - connection.candidateAt < TimeUnit.SECONDS.toNanos(1)
            || !progress.confirmIdle(connection.candidate)) continue;
        connection.draining = true;
        connection
            .send(
                SseEmitter.event()
                    .name("stream-idle")
                    .id(connection.id + "/" + candidate.activityVersion())
                    .data(
                        encode(
                            Map.of(
                                "connectionId",
                                connection.id,
                                "activityVersion",
                                Long.toString(candidate.activityVersion())))))
            .whenComplete((sent, error) -> close(connection.id));
      }
  }

  public void heartbeat() {
    for (var connection : connections.values())
      if (!connection.draining) connection.send(SseEmitter.event().comment("heartbeat"));
  }

  public void close(UUID id) {
    var connection = connections.remove(id);
    if (connection != null) connection.close();
  }

  @PreDestroy
  @org.springframework.context.event.EventListener(
      org.springframework.context.event.ContextClosedEvent.class)
  public void closeAll() {
    for (var id : List.copyOf(connections.keySet())) close(id);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (java.io.IOException invalid) {
      throw new IllegalStateException("事件编码失败");
    }
  }

  private final class Connection {
    final UUID id = UUID.randomUUID();
    final SseEmitter emitter = emitters.get();
    final AtomicBoolean closed = new AtomicBoolean();
    final CompletableFuture<Void> initialized = new CompletableFuture<>();
    final Set<CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();
    final ThreadPoolExecutor sender =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(capacity),
            Thread.ofPlatform().daemon().name("task-sse-" + id).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    long activity;
    IdleCandidate candidate;
    long candidateAt;
    volatile boolean draining;

    CompletableFuture<Boolean> send(SseEmitter.SseEventBuilder event) {
      var result = new CompletableFuture<Boolean>();
      if (closed.get()) {
        result.complete(false);
        return result;
      }
      pending.add(result);
      try {
        sender.execute(
            () -> {
              try {
                initialized.get();
                if (closed.get()) result.complete(false);
                else {
                  emitter.send(event);
                  result.complete(true);
                }
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                result.complete(false);
                SseConnectionRegistry.this.close(id);
                close();
              } catch (java.io.IOException | ExecutionException | RuntimeException failed) {
                result.complete(false);
                SseConnectionRegistry.this.close(id);
                close();
              } finally {
                pending.remove(result);
              }
            });
      } catch (RejectedExecutionException full) {
        result.complete(false);
        SseConnectionRegistry.this.close(id);
        close();
      }
      return result;
    }

    void close() {
      if (!closed.compareAndSet(false, true)) return;
      initialized.completeExceptionally(new IllegalStateException("连接已释放"));
      pending.forEach(item -> item.complete(false));
      pending.clear();
      sender.shutdownNow();
      try {
        emitter.complete();
      } catch (IllegalStateException alreadyRecycled) {
        /* 已结束的Servlet响应。 */
      }
    }
  }
}
