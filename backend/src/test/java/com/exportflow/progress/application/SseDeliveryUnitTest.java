// [规格与进度](../../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-085](../../../../../../../../docs/product/acceptance-criteria.md#ac-085)
package com.exportflow.progress.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.exporttasks.api.TaskManagementApiMapper;
import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import com.exportflow.progress.domain.ProgressModels.IdleCandidate;
import com.exportflow.progress.port.ProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseDeliveryUnitTest {
  private SseConnectionRegistry registry(SseEmitter emitter, int capacity) throws Exception {
    var progress = mock(ProgressRepository.class);
    when(progress.prepareIdle()).thenReturn(new IdleCandidate(1, true));
    var ctor =
        SseConnectionRegistry.class.getDeclaredConstructor(
            ProgressRepository.class,
            TaskManagementApiMapper.class,
            ObjectMapper.class,
            Supplier.class,
            int.class);
    ctor.setAccessible(true);
    var mapper = mock(TaskManagementApiMapper.class);
    when(mapper.toDto(any(TaskListItem.class)))
        .thenReturn(mock(com.exportflow.exporttasks.api.TaskManagementApiModels.TaskDto.class));
    var json = mock(ObjectMapper.class);
    when(json.writeValueAsString(any())).thenReturn("{}");
    return ctor.newInstance(progress, mapper, json, (Supplier<SseEmitter>) () -> emitter, capacity);
  }

  private void activate(SseConnectionRegistry registry, SseEmitter emitter) throws Exception {
    registry.getClass().getMethod("activate", SseEmitter.class).invoke(registry, emitter);
  }

  @Test
  void TC411_sendRequiresServletInitializationAndCompletesAfterActualWrite() throws Exception {
    var initialized = new AtomicBoolean();
    var written = new CountDownLatch(2);
    var emitter =
        new SseEmitter(0L) {
          @Override
          public void send(SseEventBuilder event) {
            assertThat(initialized).as("不能将Spring初始化前的内存缓冲当网络发送").isTrue();
            written.countDown();
          }
        };
    var registry = registry(emitter, 2);
    try {
      registry.open();
      var sent = registry.broadcast(2, mock(TaskListItem.class)).toCompletableFuture();
      initialized.set(true);
      activate(registry, emitter);
      assertThat(written.await(10, TimeUnit.SECONDS)).as("仅框架防挂死").isTrue();
      assertThat(sent.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      registry.closeAll();
    }
  }

  @Test
  void TC411_boundedQueueOverflowClosesConnectionAndRejectsPendingSends() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var closed = new AtomicBoolean();
    var emitter =
        new SseEmitter(0L) {
          @Override
          public void send(SseEventBuilder event) throws IOException {
            entered.countDown();
            try {
              if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("测试屏障未释放");
            } catch (InterruptedException stopped) {
              Thread.currentThread().interrupt();
              throw new IOException(stopped);
            }
          }

          @Override
          public void complete() {
            closed.set(true);
          }
        };
    var registry = registry(emitter, 2);
    try {
      registry.open();
      activate(registry, emitter);
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      var first = registry.broadcast(2, mock(TaskListItem.class)).toCompletableFuture();
      registry.broadcast(3, mock(TaskListItem.class));
      var overflow = registry.broadcast(4, mock(TaskListItem.class)).toCompletableFuture();
      assertThat(overflow.get(10, TimeUnit.SECONDS)).isFalse();
      assertThat(first.get(10, TimeUnit.SECONDS)).isFalse();
      assertThat(closed).isTrue();
    } finally {
      release.countDown();
      registry.closeAll();
    }
  }
}
