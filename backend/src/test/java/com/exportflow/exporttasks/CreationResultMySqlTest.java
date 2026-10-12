// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import org.junit.jupiter.api.Test;

// [AC-050](../../../../../../../docs/product/acceptance-criteria.md#ac-050)
// [AC-072](../../../../../../../docs/product/acceptance-criteria.md#ac-072)
class CreationResultMySqlTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Test
  void TC219_replayAfterRealSuccessfulGenerationKeepsOneTaskCommandAndArtifact() throws Exception {
    String key = newKey();
    var first = post(filtered(), key);
    assertThat(first.statusCode()).isEqualTo(201);
    var task = UUID.fromString(body(first).get("taskId").asText());
    awaitSuccess(task);
    byte[] original = download(task);
    var replay = post(filtered(), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(first));
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertThat(download(task)).isEqualTo(original);
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(count("export_artifacts")).isEqualTo(1);
  }

  @Test
  void TC219_createdAndReplayedResponsesExposeOnlyCommittedCreationResultWithoutLocation()
      throws Exception {
    String key = newKey();
    var first = post(filtered(), key);
    assertThat(first.statusCode()).isEqualTo(201);
    var result = body(first);
    var names = new HashSet<String>();
    result.fieldNames().forEachRemaining(names::add);
    assertThat(names)
        .containsExactlyInAnyOrder(
            "taskId", "taskNo", "scopeType", "totalCount", "fields", "createdAt");
    assertThat(first.headers().firstValue("Location")).isEmpty();
    var replay = post(filtered(), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(result);
    assertThat(replay.headers().firstValue("Location")).isEmpty();
    assertThat(get("/api/v1/export-tasks/" + result.get("taskId").asText()).statusCode())
        .isEqualTo(404);
  }

  @Test
  void TC219_replayAfterRealDispatchTimeoutKeepsCreationIdentityAndCreatesNoNewCommand()
      throws Exception {
    String key = newKey();
    var first = post(filtered(), key);
    assertThat(first.statusCode()).isEqualTo(201);
    var original = body(first);
    String task = original.get("taskId").asText();
    db.update(
        "UPDATE command_outbox SET dispatch_deadline=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task);
    call("com.exportflow.dispatch.application.OutboxTimeoutService", "scanExpired");
    assertThat(state(UUID.fromString(task))).isEqualTo("FAILED");
    var replay = post(filtered(), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(original);
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT status FROM command_outbox WHERE task_id=?", String.class, task))
        .isEqualTo("CLOSED");
  }
}
