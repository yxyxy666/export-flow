package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.exportflow.testing.TaskManagementFixture;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// [AC-096](../../../../../../../docs/product/acceptance-criteria.md#ac-096)
// [AC-099](../../../../../../../docs/product/acceptance-criteria.md#ac-099)
// [AC-103](../../../../../../../docs/product/acceptance-criteria.md#ac-103)
class TaskDeletionContractTest extends TaskManagementFixture {
  @Test
  void TC615_terminalDeleteRemovesTaskBusinessRecordsAndLeavesNoListItem() throws Exception {
    var task = create();
    var version = listedTask(task).path("version").asText();
    var operation = UUID.randomUUID();
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/" + task))
                .header("Content-Type", "application/json")
                .method(
                    "DELETE",
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            java.util.Map.of(
                                "operationId", operation.toString(), "expectedVersion", version))))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(202);
    assertThat(listed().path("items")).isEmpty();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM export_tasks WHERE task_id=?", Integer.class, task))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM task_deletion_tombstones WHERE task_id=?", Integer.class, task))
        .isEqualTo(1);
  }
}
