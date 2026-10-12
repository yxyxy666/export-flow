// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.testing.ProductionBackendFixture;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
class ExportCommandHandlerTest extends ProductionBackendFixture {
  @Test
  void TC223_committedTimeoutTerminalAcknowledgesDuplicateWithoutGenerating() throws Exception {
    var task = create();
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(task)).isEqualTo("FAILED");
    var channel = mock(Channel.class);
    call(
        "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener",
        "onMessage",
        command(task),
        channel,
        19L);
    verify(channel).basicAck(19L, false);
    assertThat(state(task)).isEqualTo("FAILED");
    assertThat(count("export_artifacts")).isZero();
  }

  @Test
  void TC223_activeLeaseDuplicateNeverAcknowledgesOrStartsSecondGeneration() throws Exception {
    var task = create();
    call(
        "com.exportflow.worker.application.TaskExecutionService",
        "claim",
        task,
        "synthetic-active-owner");
    assertThat(state(task)).isEqualTo("RUNNING");
    long fence =
        db.queryForObject(
            "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString());
    var channel = mock(Channel.class);
    call(
        "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener",
        "onMessage",
        command(task),
        channel,
        20L);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
    assertThat(count("export_artifacts")).isZero();
    assertThat(
            db.queryForObject(
                "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(fence);
  }

  protected byte[] command(java.util.UUID task) throws Exception {
    String event =
        db.queryForObject(
            "SELECT event_id FROM command_outbox WHERE task_id=?", String.class, task.toString());
    return json.writeValueAsBytes(
        java.util.Map.of(
            "eventId",
            event,
            "taskId",
            task.toString(),
            "schemaVersion",
            1,
            "type",
            "EXPORT_REQUESTED",
            "traceId",
            "synthetic-worker-test"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-json",
        "{}",
        "{\"eventId\":\"invalid\",\"taskId\":\"invalid\",\"schemaVersion\":999,\"type\":\"EXPORT_REQUESTED\"}"
      })
  void TC223_badMessageNeverAcknowledgesOrGeneratesFile(String payload) throws Exception {
    var channel = mock(Channel.class);
    try {
      call(
          "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener",
          "onMessage",
          payload.getBytes(StandardCharsets.UTF_8),
          channel,
          1L);
    } catch (java.lang.reflect.InvocationTargetException classifiedFailure) {
      /* 受控异常允许交由容器停止槽。 */
    }
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
    verify(channel, never()).basicReject(anyLong(), eq(false));
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), eq(false));
    assertThat(count("export_artifacts")).isZero();
  }
}
