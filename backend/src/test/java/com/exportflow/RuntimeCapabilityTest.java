// 规格与进度：[任务记录](../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;

// [AC-049](../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-068](../../../../../../docs/product/acceptance-criteria.md#ac-068)
class RuntimeCapabilityTest extends BoundaryFixture {
  private boolean enable;

  @Override
  protected boolean consumerEnabled() {
    return enable;
  }

  @Test
  void TC225_unusableRealFileRootRejectsConsumerStartupBeforeClaim() throws Exception {
    var task = create();
    app.close();
    root = Files.createTempFile(root, "synthetic-not-a-directory-", ".txt");
    enable = true;
    assertThatThrownBy(
            () -> startApplication(Class.forName("com.exportflow.ExportFlowApplication")))
        .isInstanceOf(Exception.class);
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(probe.calls(EXECUTION, "claim")).isZero();
  }

  @Test
  void TC225_atomicMoveCapabilityFailureRejectsStartupBeforeClaim() throws Exception {
    var task = create();
    app.close();
    enable = true;
    var reached = new java.util.concurrent.atomic.AtomicBoolean();
    // 仅替换难稳定触发的本机能力故障；其余Files调用仍走真实文件系统。
    try (var files =
        org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.move(
                      org.mockito.ArgumentMatchers.any(Path.class),
                      org.mockito.ArgumentMatchers.any(Path.class),
                      org.mockito.ArgumentMatchers.any(java.nio.file.CopyOption[].class)))
          .thenAnswer(
              invocation -> {
                var options = (java.nio.file.CopyOption[]) invocation.getRawArguments()[2];
                if (java.util.Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                  reached.set(true);
                  throw new AtomicMoveNotSupportedException(
                      "synthetic-source", "synthetic-target", "synthetic unavailable capability");
                }
                return invocation.callRealMethod();
              });
      assertThatThrownBy(
              () -> startApplication(Class.forName("com.exportflow.ExportFlowApplication")))
          .isInstanceOf(Exception.class);
    }
    assertThat(reached).isTrue();
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(probe.calls(EXECUTION, "claim")).isZero();
  }

  @Test
  void TC224_realBrokerAckTimeoutIsIndependentOfClaimMonitor() throws Exception {
    var result =
        rabbit.execInContainer(
            "rabbitmqctl", "eval", "application:get_env(rabbit, consumer_timeout).");
    assertThat(result.getExitCode()).isZero();
    assertThat(result.getStdout()).contains("{ok,");
    // 共享服务的全局ACK超时不改动；本项目队列单独设置规格要求的10分钟。
    var management =
        app.getBean(com.exportflow.dispatch.infrastructure.messaging.RabbitManagementClient.class);
    var queue =
        management.get(
            "/api/queues/"
                + com.exportflow.dispatch.infrastructure.messaging.RabbitManagementClient.encode(
                    rabbit.getVirtualHost())
                + "/export-flow.export.requested.v1",
            System.nanoTime() + Duration.ofSeconds(2).toNanos());
    assertThat(queue.path("arguments").path("x-consumer-timeout").asLong()).isEqualTo(600000);
    String configured = app.getEnvironment().getProperty("export-flow.execution.claim-timeout");
    if (configured != null) assertThat(configured).isIn("5s", "PT5S");
    // 未显式覆盖时，由ExecutionSupervisionTest的实际五秒边界验证默认值。
  }

  @Test
  void TC224_databaseAuthorityIgnoresStaleHostLeaseTimestamp() throws Exception {
    var task = create();
    var lease = lease(task);
    Object oldHostView = copy(lease, java.util.Map.of("leaseUntil", Instant.EPOCH));
    assertThat(call(EXEC_SERVICE, "renew", oldHostView).toString()).isEqualTo("APPLIED");
    db.update(
        "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    Object futureHostView =
        copy(lease, java.util.Map.of("leaseUntil", Instant.parse("2099-01-01T00:00:00Z")));
    assertThat(call(EXEC_SERVICE, "renew", futureHostView).toString()).isEqualTo("STALE");
  }

  @Test
  void TC224_shutdownStopsActualConsumerAndDoesNotClaimNewMessages() throws Exception {
    enable = true;
    restartApplication();
    var registry =
        app.getBean(org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class);
    assertThat(registry.getListenerContainers()).isNotEmpty();
    assertThat(registry.getListenerContainers()).allMatch(c -> c.isRunning());
    call("com.exportflow.worker.application.ExecutionSupervisor", "stop", Duration.ofSeconds(15));
    assertThat(registry.getListenerContainers()).allMatch(c -> !c.isRunning());
    var task = create();
    call(DISPATCH, "scanAvailable");
    assertThat(state(task)).isEqualTo("PENDING");
    assertThat(probe.calls(GENERATOR, "generate")).isZero();
    try (var connection = brokerConnection();
        var channel = connection.createChannel()) {
      assertThat(channel.messageCount("export-flow.export.requested.v1")).isEqualTo(1);
    }
  }
}
