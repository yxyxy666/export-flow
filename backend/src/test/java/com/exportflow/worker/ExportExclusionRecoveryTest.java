// 规格与进度：[任务记录](../../../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.exporttasks.domain.ExportFileModels.CheckpointSnapshot;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import com.exportflow.testing.BoundaryFixture;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// [AC-079](../../../../../../../docs/product/acceptance-criteria.md#ac-079)
// [AC-075](../../../../../../../docs/product/acceptance-criteria.md#ac-075)
// [AC-077](../../../../../../../docs/product/acceptance-criteria.md#ac-077)
class ExportExclusionRecoveryTest extends BoundaryFixture {
  @Test
  void TC316_smallRealWorkbookResumesCommittedPrefixWithoutReintroducingCanceledRow()
      throws Exception {
    var ids = db.queryForList("SELECT id FROM orders ORDER BY id", Long.class);
    var response = post(excludedPayload(ids.get(0)), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(body(response).get("totalCount").asLong()).isEqualTo(3);
    var task = UUID.fromString(body(response).get("taskId").asText());
    call(DISPATCH, "scanAvailable");
    var original = startWorker(true);
    try {
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(original.isAlive()).isTrue();
                assertThat(root.resolve("block-committed.marker")).exists();
              });
      var trusted =
          db.queryForList(
              "SELECT block_id,checksum,configuration_hash FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
              task.toString());
      assertThat(trusted).hasSize(1);
      assertThat(
              db.queryForObject(
                  "SELECT processed_count FROM export_tasks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(3);
      original.destroyForcibly();
      assertThat(original.waitFor(20, TimeUnit.SECONDS)).isTrue();
      db.update(
          "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
          task.toString());
      var resumed = startWorker(false);
      try {
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(state(task)).isEqualTo("SUCCEEDED"));
      } finally {
        stopWorker(resumed);
      }
      assertThat(
              db.queryForList(
                  "SELECT block_id,checksum,configuration_hash FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
                  task.toString()))
          .isEqualTo(trusted);
      assertWorkbook(task, ids.get(0));
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM command_outbox WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                  Long.class,
                  task.toString()))
          .isEqualTo(1);
    } finally {
      if (original.isAlive()) stopWorker(original);
    }
  }

  @Test
  void TC316_persistedExclusionAfterRestartGeneratesTheExactThreeSourceRows() throws Exception {
    var excluded = db.queryForObject("SELECT MIN(id) FROM orders", Long.class);
    var task = seededExclusion(excluded);
    restartApplication();
    var lease = lease(task);
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertWorkbook(task, excluded);
  }

  @Test
  void TC316_wrongConfigurationPrefixIsRejectedAndRebuiltForPersistedExclusion() throws Exception {
    var excluded = db.queryForObject("SELECT MIN(id) FROM orders", Long.class);
    var task = seededExclusion(excluded);
    var lease = lease(task);
    String originalConfig =
        app.getBean(ExportTaskRepository.class)
            .findSnapshot(task)
            .orElseThrow()
            .configurationHash();
    db.update(
        "INSERT INTO export_checkpoints(task_id,data_generation,configuration_hash,format_version) VALUES (?,1,?,1)",
        task.toString(),
        originalConfig);
    var injected = new AtomicBoolean();
    probe.on(
        CHECKPOINT,
        "find",
        (method, args, real) -> {
          var current = (CheckpointSnapshot) real.run();
          if (injected.compareAndSet(false, true))
            return copy(current, Map.of("configurationHash", "0".repeat(64)));
          return current;
        });
    try {
      var result = generate(task, lease);
      assertThat(injected.get()).as("必须实际触发错误配置前缀").isTrue();
      assertThat(property(result, "kind").toString()).isEqualTo("READY");
      assertThat(
              db.queryForObject(
                  "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isGreaterThan(1);
      String config =
          app.getBean(ExportTaskRepository.class)
              .findSnapshot(task)
              .orElseThrow()
              .configurationHash();
      assertThat(
              db.queryForList(
                  "SELECT DISTINCT configuration_hash FROM export_data_blocks WHERE task_id=?",
                  String.class,
                  task.toString()))
          .containsExactly(config);
    } finally {
      probe.clear();
    }
  }

  private UUID seededExclusion(Long excluded) throws Exception {
    // 仅构造测试schema中的已受理任务状态，来源仍只读。
    var task = create();
    db.update(
        "INSERT INTO export_task_orders(task_id,order_id) VALUES (?,?)", task.toString(), excluded);
    db.update("UPDATE export_tasks SET total_count=3 WHERE task_id=?", task.toString());
    return task;
  }

  private String excludedPayload(Long excluded) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "scope",
            Map.of(
                "type",
                "FILTERED",
                "filter",
                Map.of(),
                "excludedOrderIds",
                List.of(excluded.toString())),
            "fields",
            List.of("orderNo")));
  }

  private void assertWorkbook(UUID task, Long excluded) throws Exception {
    var expected =
        db.queryForList(
            "SELECT order_no FROM orders WHERE id<>? ORDER BY created_at DESC,id DESC",
            String.class,
            excluded);
    String canceled =
        db.queryForObject("SELECT order_no FROM orders WHERE id=?", String.class, excluded);
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(download(task)))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(1);
      var sheet = book.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isEqualTo(3);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("订单号");
      var actual = new ArrayList<String>();
      for (int row = 1; row <= sheet.getLastRowNum(); row++)
        actual.add(sheet.getRow(row).getCell(0).getStringCellValue());
      assertThat(actual).containsExactlyElementsOf(expected).doesNotContain(canceled);
    }
  }
}
