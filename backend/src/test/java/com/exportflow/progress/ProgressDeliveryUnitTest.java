// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-085](../../../../../../../docs/product/acceptance-criteria.md#ac-085)
package com.exportflow.progress;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import com.exportflow.exporttasks.port.TaskManagementRepository;
import com.exportflow.progress.application.*;
import com.exportflow.progress.domain.ProgressModels.*;
import com.exportflow.progress.infrastructure.messaging.RabbitProgressListener;
import com.exportflow.progress.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;

class ProgressDeliveryUnitTest {
  final ProgressRepository progress = mock(ProgressRepository.class);
  final ProgressCache cache = mock(ProgressCache.class);
  final ProgressPublisher publisher = mock(ProgressPublisher.class);
  final TaskManagementRepository tasks = mock(TaskManagementRepository.class);
  final SseConnectionRegistry connections = mock(SseConnectionRegistry.class);
  final TaskProgressService service =
      new TaskProgressService(
          progress, cache, publisher, tasks, connections, new RuntimeProperties());
  final UUID id = UUID.randomUUID();
  final TaskListItem task = mock(TaskListItem.class);
  final ProgressNotice notice = new ProgressNotice(1, id, 3, 7);

  @Test
  void TC406_cacheWriteFailureDoesNotPreventReliablePublication() {
    var lease = new NoticeLease(id, 3, 7, "owner", UUID.randomUUID());
    when(progress.claimPending(anyString(), eq(20))).thenReturn(List.of(lease));
    when(tasks.find(id)).thenReturn(task);
    when(task.version()).thenReturn(3L);
    when(task.taskId()).thenReturn(id);
    doThrow(new IllegalStateException("synthetic cache failure")).when(cache).putIfNewer(task);
    var proof = new PublishProof(true, true);
    when(publisher.publish(notice)).thenReturn(proof);
    when(progress.markPublished(lease, proof)).thenReturn(true);
    assertThat(service.dispatchPending(20).published()).isEqualTo(1);
    verify(publisher).publish(notice);
  }

  @Test
  void TC406_cacheReadFailureFallsBackToAuthoritativeDatabase() {
    when(cache.get(id, 3)).thenThrow(new IllegalStateException("synthetic cache failure"));
    when(tasks.find(id)).thenReturn(task);
    when(task.version()).thenReturn(4L);
    when(connections.broadcast(7, task)).thenReturn(CompletableFuture.completedFuture(true));
    assertThat(service.onNotice(notice)).isTrue();
    verify(tasks).find(id);
  }

  @Test
  void TC406_databaseUnavailableRetainsDelivery() {
    when(cache.get(id, 3)).thenReturn(Optional.empty());
    when(tasks.find(id)).thenThrow(new IllegalStateException("synthetic database failure"));
    assertThat(service.onNotice(notice)).isFalse();
    verifyNoInteractions(connections);
  }

  @Test
  void TC411_listenerDoesNotAckUntilLocalSendCompletesAndFailureRetainsMessage() throws Exception {
    when(cache.get(id, 3)).thenReturn(Optional.of(task));
    when(task.version()).thenReturn(3L);
    var entered = new CountDownLatch(1);
    var sent = new CompletableFuture<Boolean>();
    when(connections.broadcast(7, task))
        .thenAnswer(
            call -> {
              entered.countDown();
              return sent;
            });
    var mapper = new ObjectMapper();
    var listener = new RabbitProgressListener(service, mapper);
    var properties = new MessageProperties();
    properties.setDeliveryTag(19);
    var message = new Message(mapper.writeValueAsBytes(notice), properties);
    var channel = mock(Channel.class);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var delivery =
          executor.submit(
              () -> {
                listener.receive(message, channel);
                return null;
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).as("仅框架防挂死").isTrue();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
      } finally {
        sent.complete(true);
      }
      delivery.get(10, TimeUnit.SECONDS);
      verify(channel).basicAck(19, false);
    }
    when(connections.broadcast(7, task)).thenReturn(CompletableFuture.completedFuture(false));
    var failed = mock(Channel.class);
    listener.receive(message, failed);
    verify(failed, never()).basicAck(anyLong(), anyBoolean());
    verify(failed).abort();
  }
}
