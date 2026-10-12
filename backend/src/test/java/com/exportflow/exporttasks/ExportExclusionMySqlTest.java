// 规格与进度：[任务记录](../../../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.orders.port.OrderRepository;
import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import org.junit.jupiter.api.Test;

// [AC-079](../../../../../../../docs/product/acceptance-criteria.md#ac-079)
// [AC-075](../../../../../../../docs/product/acceptance-criteria.md#ac-075)
// [AC-076](../../../../../../../docs/product/acceptance-criteria.md#ac-076)
// [AC-019](../../../../../../../docs/product/acceptance-criteria.md#ac-019)
// [AC-008](../../../../../../../docs/product/acceptance-criteria.md#ac-008)
class ExportExclusionMySqlTest extends ProductionBackendFixture {
  @Test
  void TC314_countAssociationsAndReadOnlySourceMatchTheActualExclusionIntersection()
      throws Exception {
    var ids = ids();
    var sourceBefore =
        db.queryForList("SELECT id,order_no,amount,created_at FROM orders ORDER BY id");
    var response = post(payload(Map.of(), List.of(ids.get(1), ids.get(0), ids.get(0))), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(body(response).get("totalCount").asLong()).isEqualTo(2);
    String task = body(response).get("taskId").asText();
    assertThat(
            db.queryForList(
                "SELECT order_id FROM export_task_orders WHERE task_id=? ORDER BY order_id",
                Long.class,
                task))
        .containsExactly(ids.get(0), ids.get(1));
    assertThat(db.queryForList("SELECT id,order_no,amount,created_at FROM orders ORDER BY id"))
        .isEqualTo(sourceBefore);
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("export_idempotency")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  @Test
  void TC314_existingExclusionOutsideFilterDoesNotDecreaseCount() throws Exception {
    var ids = ids();
    String number =
        db.queryForObject("SELECT order_no FROM orders WHERE id=?", String.class, ids.get(0));
    var response = post(payload(Map.of("orderNo", number), List.of(ids.get(1))), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(body(response).get("totalCount").asLong()).isEqualTo(1);
    var task = UUID.fromString(body(response).get("taskId").asText());
    assertThat(readAll(task)).containsExactly(ids.get(0));
  }

  @Test
  void TC314_emptyAndMissingExclusionsRejectWithoutWritingAnything() throws Exception {
    var before = creationCounts();
    var empty = post(payload(Map.of(), ids()), newKey());
    assertThat(empty.statusCode()).isEqualTo(400);
    assertThat(body(empty).get("code").asText()).isEqualTo("EMPTY_EXPORT_SCOPE");
    assertThat(creationCounts()).isEqualTo(before);
    var missing = post(payload(Map.of(), List.of(Long.MAX_VALUE)), newKey());
    assertThat(missing.statusCode()).isEqualTo(422);
    assertThat(body(missing).get("code").asText()).isEqualTo("ORDER_SELECTION_INVALID");
    assertThat(creationCounts()).isEqualTo(before);
  }

  @Test
  void TC314_exclusionAssociationFailureRollsBackTaskKeyAndOutbox() throws Exception {
    try (var setup = mysql.getAdminConnection();
        var ddl = setup.createStatement()) {
      ddl.execute(
          "CREATE TRIGGER test_fail_exclusion BEFORE INSERT ON export_task_orders FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test-only-exclusion-write'");
      try {
        var before = creationCounts();
        var response = post(payload(Map.of(), List.of(ids().get(0))), newKey());
        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(creationCounts()).isEqualTo(before);
        assertThat(response.body()).doesNotContain("test-only-exclusion-write", "SQLSTATE");
      } finally {
        ddl.execute("DROP TRIGGER test_fail_exclusion");
      }
    }
  }

  @Test
  void TC314_exclusionWritesAndCountsSpanTheThousandIdBatchBoundary() throws Exception {
    // 仅1002笔既有来源用于两个批次的功能断言；只创建任务，不生成大文件或测量性能。
    useOrderVolume(1002);
    var source = ids();
    var exclusions = source.subList(0, 1001);
    var response = post(payload(Map.of(), exclusions), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(body(response).get("totalCount").asLong()).isEqualTo(1);
    var task = UUID.fromString(body(response).get("taskId").asText());
    assertThat(count("export_task_orders")).isEqualTo(1001);
    assertThat(readAll(task)).containsExactly(source.get(1001));
    String number =
        db.queryForObject("SELECT order_no FROM orders WHERE id=?", String.class, source.get(1001));
    var outside = post(payload(Map.of("orderNo", number), exclusions), newKey());
    assertThat(outside.statusCode()).isEqualTo(201);
    assertThat(body(outside).get("totalCount").asLong()).isEqualTo(1);
  }

  @Test
  void TC315_legacySelectedAndFilteredSnapshotsReadExactlyTheirOriginalRanges() throws Exception {
    var ids = ids();
    var filteredTask = create();
    var selected =
        post(
            json.writeValueAsString(
                Map.of(
                    "scope",
                    Map.of("type", "SELECTED", "orderIds", List.of(ids.get(0).toString())),
                    "fields",
                    List.of("orderNo"))),
            newKey());
    assertThat(selected.statusCode()).isEqualTo(201);
    var selectedTask = UUID.fromString(body(selected).get("taskId").asText());
    assertThat(readAll(filteredTask)).containsExactlyInAnyOrderElementsOf(ids);
    assertThat(readAll(selectedTask)).containsExactly(ids.get(0));
    var snapshot = app.getBean(ExportTaskRepository.class).findSnapshot(filteredTask).orElseThrow();
    // 旧配置身份固定字节：8个null筛选项、原有序字段。新record不能改变它。
    assertThat(snapshot.configurationHash()).isEqualTo(legacyConfigurationHash());
  }

  @Test
  void TC315_persistedFilteredAssociationsExcludeRowsAcrossKeysetPagesAfterRestart()
      throws Exception {
    var ids = ids();
    var task = create();
    // 仅写本用例任务库，模拟本版已受理的持久范围，绕过尚未实现的HTTP字段以独立验证批读。
    db.update(
        "INSERT INTO export_task_orders(task_id,order_id) VALUES (?,?)",
        task.toString(),
        ids.get(1));
    db.update("UPDATE export_tasks SET total_count=3 WHERE task_id=?", task.toString());
    assertThat(readAll(task)).containsExactlyInAnyOrder(ids.get(0), ids.get(2), ids.get(3));
    var before =
        app.getBean(ExportTaskRepository.class)
            .findSnapshot(task)
            .orElseThrow()
            .configurationHash();
    assertThat(before).isNotEqualTo(legacyConfigurationHash());
    restartApplication();
    assertThat(readAll(task)).containsExactlyInAnyOrder(ids.get(0), ids.get(2), ids.get(3));
    assertThat(
            app.getBean(ExportTaskRepository.class)
                .findSnapshot(task)
                .orElseThrow()
                .configurationHash())
        .isEqualTo(before);
  }

  @Test
  void TC315_differentExclusionSetsHaveDifferentConfigurationIdentities() throws Exception {
    var ids = ids();
    var task = create();
    db.update(
        "INSERT INTO export_task_orders(task_id,order_id) VALUES (?,?)",
        task.toString(),
        ids.get(0));
    db.update("UPDATE export_tasks SET total_count=3 WHERE task_id=?", task.toString());
    var tasks = app.getBean(ExportTaskRepository.class);
    String first = tasks.findSnapshot(task).orElseThrow().configurationHash();
    db.update(
        "UPDATE export_task_orders SET order_id=? WHERE task_id=?", ids.get(1), task.toString());
    String second = tasks.findSnapshot(task).orElseThrow().configurationHash();
    assertThat(second).isNotEqualTo(first);
  }

  private List<Long> ids() {
    return db.queryForList("SELECT id FROM orders ORDER BY id", Long.class);
  }

  private String payload(Map<String, String> filter, List<Long> exclusions) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "scope",
            Map.of(
                "type",
                "FILTERED",
                "filter",
                filter,
                "excludedOrderIds",
                exclusions.stream().map(Object::toString).toList()),
            "fields",
            List.of("orderNo")));
  }

  private List<Long> readAll(UUID task) {
    var scope = app.getBean(ExportTaskRepository.class).findSnapshot(task).orElseThrow().scope();
    var orders = app.getBean(OrderRepository.class);
    List<Long> result = new ArrayList<>();
    OrderCursor cursor = null;
    for (int i = 0; i < 5; i++) {
      var batch = orders.readBatch(task, scope, cursor, 1);
      result.addAll(batch.rows().stream().map(Order::id).toList());
      if (batch.isEnd()) return result;
      assertThat(batch.lastCursor()).isNotEqualTo(cursor);
      cursor = batch.lastCursor();
    }
    throw new AssertionError("小范围keyset读取未结束");
  }

  private String legacyConfigurationHash() throws Exception {
    var digest = java.security.MessageDigest.getInstance("SHA-256");
    var values = new ArrayList<String>();
    values.add("configuration-v1");
    values.add("FILTERED");
    values.addAll(Collections.nCopies(8, null));
    values.add("[orderNo, amount, createdAt]");
    for (String value : values) {
      byte[] bytes =
          value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      digest.update(
          java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
      digest.update(bytes);
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
