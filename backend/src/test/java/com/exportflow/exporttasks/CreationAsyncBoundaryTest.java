// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// [AC-011](../../../../../../../docs/product/acceptance-criteria.md#ac-011)
// [SC-010](../../../../../../../docs/product/acceptance-criteria.md#sc-010)
class CreationAsyncBoundaryTest extends BoundaryFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @Test
  void TC218_httpCommitsWithoutWaitingForBlockedRealGenerator() throws Exception {
    try (var gate = new BoundaryProbe.Gate()) {
      probe.on(
          GENERATOR,
          "generate",
          (m, a, real) -> {
            gate.pause();
            return real.run();
          });
      long started = System.nanoTime();
      var response = post(filtered(), newKey());
      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
      var task = UUID.fromString(body(response).get("taskId").asText());
      gate.await();
      assertThat(count("export_tasks")).isEqualTo(1);
      assertThat(count("export_idempotency")).isEqualTo(1);
      assertThat(count("command_outbox")).isEqualTo(1);
      assertThat(state(task)).isEqualTo("RUNNING");
      assertThat(count("export_artifacts")).isZero();
      gate.close();
      awaitSuccess(task);
    }
  }

  @Test
  void TC218_httpAcceptsDuringRealBrokerOutageAndOriginalOutboxResumes() throws Exception {
    rabbit.suspendConnections();
    UUID task;
    try {
      long started = System.nanoTime();
      var response = post(filtered(), newKey());
      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
      task = UUID.fromString(body(response).get("taskId").asText());
      assertThat(state(task)).isEqualTo("PENDING");
      assertThat(count("command_outbox")).isEqualTo(1);
    } finally {
      rabbit.resumeConnections();
    }
    Object event = outbox(task).get("event_id");
    awaitSuccess(task);
    assertThat(outbox(task).get("event_id")).isEqualTo(event);
    assertThat(count("command_outbox")).isEqualTo(1);
  }
}
