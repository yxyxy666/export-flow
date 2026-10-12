// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.testing.*;
import com.rabbitmq.client.Channel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
class WorkerFailureWindowsTest extends BoundaryFixture {
  private static final String LISTENER =
      "com.exportflow.worker.infrastructure.messaging.RabbitMqExportCommandListener";

  @Test
  void TC223_eventForAnotherTaskNeverClaimsOrAcknowledges() throws Exception {
    var first = create();
    var other = create();
    var payload = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(command(first));
    payload.put("eventId", outbox(other).get("event_id").toString());
    var channel = mock(Channel.class);
    call(LISTENER, "onMessage", json.writeValueAsBytes(payload), channel, 92L);
    noAck(channel);
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    assertThat(state(first)).isEqualTo("PENDING");
    assertThat(state(other)).isEqualTo("PENDING");
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(mockingDetails(channel).getInvocations())
                    .anyMatch(i -> Set.of("close", "abort").contains(i.getMethod().getName())));
  }

  @Test
  void TC223_databaseUnreadableNeverAcknowledgesOrGenerates() throws Exception {
    var task = create();
    var channel = mock(Channel.class);
    var reached = new AtomicBoolean();
    probe.on(
        TASKS,
        "findSnapshot",
        (m, a, real) -> {
          reached.set(true);
          throw new java.sql.SQLTransientConnectionException("synthetic database unavailable");
        });
    try {
      call(LISTENER, "onMessage", command(task), channel, 1L);
    } catch (java.lang.reflect.InvocationTargetException classified) {
      /* UNKNOWN或受控停止。 */
    }
    assertThat(reached).isTrue();
    noAck(channel);
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    assertThat(state(task)).isEqualTo("PENDING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "KNOWN_FAILURE", "READY"})
  void TC225_generatorResultRequiresCommittedTerminalBeforeAck(String kind) throws Exception {
    var task = create();
    var channel = mock(Channel.class);
    var completed = new AtomicBoolean();
    if (!kind.equals("READY"))
      probe.on(
          GENERATOR,
          "generate",
          (m, a, real) -> kind.equals("UNKNOWN") ? unknown() : failure("EXPORT_CELL_INVALID"));
    probe.on(
        EXECUTION,
        "complete",
        (m, a, real) -> {
          noAck(channel);
          Object result = real.run();
          assertThat(state(task)).isEqualTo(kind.equals("READY") ? "SUCCEEDED" : "FAILED");
          completed.set(true);
          return result;
        });
    call(LISTENER, "onMessage", command(task), channel, 2L);
    assertThat(probe.calls(GENERATOR, "generate")).isEqualTo(1);
    if (kind.equals("UNKNOWN")) {
      noAck(channel);
      assertThat(completed).isFalse();
      assertThat(state(task)).isEqualTo("RUNNING");
    } else {
      assertThat(completed).isTrue();
      verify(channel).basicAck(2L, false);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void TC225_lostTerminalCommitAcknowledgementIsReconciledBeforeAck(boolean unreadable)
      throws Exception {
    var task = create();
    var channel = mock(Channel.class);
    var committed = new AtomicBoolean();
    probe.on(
        EXECUTION,
        "complete",
        (m, a, real) -> {
          noAck(channel);
          Object result = real.run();
          assertThat(state(task)).isEqualTo("SUCCEEDED");
          committed.set(true);
          throw new java.sql.SQLRecoverableException("synthetic terminal response lost");
        });
    if (unreadable) {
      probe.on(
          EXECUTION,
          "find",
          (m, a, real) -> {
            if (committed.get())
              throw new java.sql.SQLTransientConnectionException("synthetic unreadable");
            return real.run();
          });
      probe.on(
          TASKS,
          "findSnapshot",
          (m, a, real) -> {
            if (committed.get())
              throw new java.sql.SQLTransientConnectionException("synthetic unreadable");
            return real.run();
          });
    }
    try {
      call(LISTENER, "onMessage", command(task), channel, 3L);
    } catch (java.lang.reflect.InvocationTargetException classified) {
      assertThat(committed).isTrue();
    }
    assertThat(committed).isTrue();
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    if (unreadable) noAck(channel);
    else verify(channel).basicAck(3L, false);
    probe.clear();
    var replay = mock(Channel.class);
    call(LISTENER, "onMessage", command(task), replay, 4L);
    verify(replay).basicAck(4L, false);
    assertThat(probe.calls(GENERATOR, "generate")).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                Long.class,
                task.toString()))
        .isEqualTo(1);
  }

  @Test
  void TC224_claimCommitUnknownCannotGenerateUntilReconciled() throws Exception {
    var task = create();
    var channel = mock(Channel.class);
    var committed = new AtomicBoolean();
    probe.on(
        EXECUTION,
        "claim",
        (m, a, real) -> {
          Object acquired = real.run();
          assertThat(property(acquired, "kind").toString()).isEqualTo("ACQUIRED");
          committed.set(true);
          throw new java.sql.SQLRecoverableException("synthetic claim ack lost");
        });
    probe.on(
        EXECUTION,
        "find",
        (m, a, real) -> {
          throw new java.sql.SQLTransientConnectionException(
              "synthetic reconciliation unavailable");
        });
    try {
      call(LISTENER, "onMessage", command(task), channel, 5L);
    } catch (java.lang.reflect.InvocationTargetException classified) {
      /* 权威无法核对。 */
    }
    assertThat(committed).isTrue();
    noAck(channel);
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC224_normalRenewExtendsLeaseButOldOwnerCannotCompleteOrRegisterReady() throws Exception {
    var task = create();
    var old = lease(task);
    var ready = generate(task, old);
    assertThat(property(ready, "kind").toString()).isEqualTo("READY");
    assertThat(call(EXEC_SERVICE, "renew", old).toString()).isEqualTo("APPLIED");
    expireExecution(task);
    var next = property(claim(task, "synthetic-new-owner"), "lease");
    assertThat(call(EXEC_SERVICE, "complete", old, ready).toString()).isEqualTo("STALE");
    assertThat(port(ARTIFACT, "registerReady", old, property(ready, "artifact")).toString())
        .isEqualTo("STALE");
    assertThat(call(EXEC_SERVICE, "complete", old, failure("EXPORT_FILE_INVALID")).toString())
        .isEqualTo("STALE");
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(call(EXEC_SERVICE, "complete", next, generate(task, next)).toString())
        .isEqualTo("APPLIED");
    assertThat(state(task)).isEqualTo("SUCCEEDED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN_TYPE", "MISSING_FIELD", "TOO_LARGE", "TASK_ABSENT"})
  void TC223_invalidCommandsCloseAffectedChannelWithoutAckOrImmediateRequeue(String defect)
      throws Exception {
    var task = create();
    var payload = json.readTree(command(task));
    if (defect.equals("UNKNOWN_TYPE"))
      ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("type", "OTHER");
    if (defect.equals("MISSING_FIELD"))
      ((com.fasterxml.jackson.databind.node.ObjectNode) payload).remove("eventId");
    if (defect.equals("TASK_ABSENT"))
      ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
          .put("taskId", UUID.randomUUID().toString());
    byte[] bytes =
        defect.equals("TOO_LARGE") ? new byte[16 * 1024 + 1] : json.writeValueAsBytes(payload);
    var channel = mock(Channel.class);
    when(channel.isOpen()).thenReturn(true);
    try {
      call(LISTENER, "onMessage", bytes, channel, 6L);
    } catch (java.lang.reflect.InvocationTargetException classified) {
      /* 受控停止允许异常出口。 */
    }
    // 停槽与关闭在独立线程执行；等待关闭边界后再核对完整的禁止ACK/重入队断言。
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(mockingDetails(channel).getInvocations())
                    .anyMatch(i -> Set.of("close", "abort").contains(i.getMethod().getName())));
    noAck(channel);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), eq(true));
    verify(channel, never()).basicReject(anyLong(), anyBoolean());
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    assertThat(count("export_artifacts")).isZero();
  }

  @Test
  void TC225_unavailableGeneratorCannotBeEnabledAtStartup() throws Exception {
    probe.on(GENERATOR, "isAvailable", (m, a, real) -> false);
    // 使用同一隔离服务/目录，仅切换测试角色开启消费者，启动必须失败。
    app.close();
    assertThatThrownBy(
            () -> {
              try (var rejected =
                  new org.springframework.boot.builder.SpringApplicationBuilder(
                          Class.forName("com.exportflow.ExportFlowApplication"))
                      .initializers(c -> c.getBeanFactory().addBeanPostProcessor(probe))
                      .run(
                          "--server.address=127.0.0.1",
                          "--server.port=0",
                          "--spring.datasource.url=" + mysql.getJdbcUrl(),
                          "--spring.datasource.username=" + mysql.getUsername(),
                          "--spring.datasource.password=" + mysql.getPassword(),
                          "--spring.rabbitmq.addresses=" + rabbit.getAmqpUrl(),
                          "--spring.rabbitmq.virtual-host=" + rabbit.getVirtualHost(),
                          "--export-flow.file-root=" + root,
                          "--export-flow.consumer-enabled=true")) {
                /* 成功启动同样关闭，断言随后失败。 */
              }
            })
        .isInstanceOf(RuntimeException.class);
    assertThat(probe.calls(GENERATOR, "isAvailable")).isPositive();
    assertThat(count("export_tasks")).isZero();
  }

  @Test
  void TC225_terminalWriteFailureBeforeCommitCannotAckOrInventFailure() throws Exception {
    var task = create();
    var channel = mock(Channel.class);
    var reached = new AtomicBoolean();
    probe.on(
        EXECUTION,
        "complete",
        (m, a, real) -> {
          reached.set(true);
          throw new java.sql.SQLTransientConnectionException("synthetic before terminal commit");
        });
    try {
      call(LISTENER, "onMessage", command(task), channel, 91L);
    } catch (java.lang.reflect.InvocationTargetException controlled) {
    }
    assertThat(reached).isTrue();
    noAck(channel);
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(
            db.queryForObject(
                "SELECT result_artifact_id FROM export_tasks WHERE task_id=?",
                String.class,
                task.toString()))
        .isNull();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='READY'",
                Long.class,
                task.toString()))
        .isEqualTo(1);
  }
}
