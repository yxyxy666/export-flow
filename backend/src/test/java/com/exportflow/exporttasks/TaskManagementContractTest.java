// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.TaskManagementFixture;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

// [AC-012](../../../../../../../docs/product/acceptance-criteria.md#ac-012)
// [AC-026](../../../../../../../docs/product/acceptance-criteria.md#ac-026)
// [AC-027](../../../../../../../docs/product/acceptance-criteria.md#ac-027)
// [AC-028](../../../../../../../docs/product/acceptance-criteria.md#ac-028)
// [AC-031](../../../../../../../docs/product/acceptance-criteria.md#ac-031)
// [AC-038](../../../../../../../docs/product/acceptance-criteria.md#ac-038)
// [AC-081](../../../../../../../docs/product/acceptance-criteria.md#ac-081)
// [AC-084](../../../../../../../docs/product/acceptance-criteria.md#ac-084)
// [AC-086](../../../../../../../docs/product/acceptance-criteria.md#ac-086)
class TaskManagementContractTest extends TaskManagementFixture {
  @Test
  void TC401_listIsAuthoritativeOrderedAndDoesNotExposeWorkerOrCustomerData() throws Exception {
    var first = create();
    var second = create();
    db.update(
        "UPDATE export_tasks SET created_at='2026-10-17 00:00:00' WHERE task_id IN (?,?)",
        first.toString(),
        second.toString());
    var result = listed();
    assertThat(result.path("total").asText()).isEqualTo("2");
    assertThat(result.path("hasActiveTasks").asBoolean()).isTrue();
    assertThat(result.path("activityVersion").isTextual()).isTrue();
    var ids = new ArrayList<String>();
    for (var item : result.path("items")) {
      ids.add(item.path("taskId").asText());
      assertThat(item.path("status").asText()).isEqualTo("PENDING");
      assertThat(item.path("processedCount").asLong()).isZero();
      assertThat(item.path("progressPercent").asInt()).isZero();
      assertThat(item.path("version").isTextual()).isTrue();
      assertThat(item.path("controlVersion").asText()).isEqualTo("0");
      assertThat(item.path("fileName").asText()).startsWith("订单导出_20261017-080000_");
      assertThat(item.has("owner")).isFalse();
      assertThat(item.has("storageRef")).isFalse();
      assertThat(item.has("customerName")).isFalse();
      assertThat(item.has("customerPhone")).isFalse();
    }
    assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void TC401_emptyOrOutOfRangePageStillIncludesGlobalActivityAndTotal() throws Exception {
    create();
    var response = get("/api/v1/export-tasks?keyword=NO_MATCH&page=2147483647&pageSize=100");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body(response).path("items")).isEmpty();
    assertThat(body(response).path("total").asText()).isEqualTo("0");
    assertThat(body(response).path("hasActiveTasks").asBoolean()).isTrue();
  }

  @Test
  void TC401_keywordMatchesDisplayedNameAndTreatsLikeWildcardsLiterally() throws Exception {
    var task = create();
    db.update("UPDATE export_tasks SET task_no='EXP%_LITERAL' WHERE task_id=?", task.toString());
    var response =
        get("/api/v1/export-tasks?keyword=" + URLEncoder.encode("%_", StandardCharsets.UTF_8));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body(response).path("items")).hasSize(1);
    var name = body(response).path("items").get(0).path("fileName").asText();
    var byName =
        get(
            "/api/v1/export-tasks?status=PENDING&keyword="
                + URLEncoder.encode(name, StandardCharsets.UTF_8));
    assertThat(byName.statusCode()).isEqualTo(200);
    assertThat(body(byName).path("items").get(0).path("taskId").asText())
        .isEqualTo(task.toString());
  }

  @Test
  void TC407_invalidDuplicateAndUnknownQueriesRejectWithoutMutation() throws Exception {
    create();
    var before = creationCounts();
    for (var query :
        List.of(
            "page=0",
            "page=-1",
            "page=1.5",
            "page=2147483648",
            "pageSize=30",
            "status=UNKNOWN",
            "page=1&page=2",
            "unknown=x",
            "keyword=" + "a".repeat(101))) {
      var response = get("/api/v1/export-tasks?" + query);
      assertThat(response.statusCode()).as(query).isEqualTo(400);
      assertThat(body(response).path("code").asText()).isEqualTo("VALIDATION_ERROR");
      assertThat(body(response).path("outcome").asText()).isEqualTo("REJECTED");
    }
    assertThat(creationCounts()).isEqualTo(before);
  }

  // [AC-013](../../../../../../../docs/product/acceptance-criteria.md#ac-013)
  // [AC-014](../../../../../../../docs/product/acceptance-criteria.md#ac-014)
  // [AC-015](../../../../../../../docs/product/acceptance-criteria.md#ac-015)
  @Test
  void TC408_successSummaryUsesActualArtifactAndPausedDownloadIsUnavailable() throws Exception {
    var task = create();
    var lease = lease(task);
    var generated = generate(task, lease);
    assertThat(port(EXECUTION, "complete", lease, generated).toString()).isEqualTo("APPLIED");
    var summary = listedTask(task);
    assertThat(summary.path("progressPercent").asInt()).isEqualTo(100);
    assertThat(summary.path("fileSize").asText()).isEqualTo(String.valueOf(download(task).length));
    assertThat(summary.path("allowedActions").toString())
        .contains("DOWNLOAD")
        .doesNotContain("PAUSE");
    var pending = create();
    var response = pause(pending, UUID.randomUUID(), "0");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(get("/api/v1/export-tasks/" + pending + "/file").statusCode()).isEqualTo(409);
  }
}
