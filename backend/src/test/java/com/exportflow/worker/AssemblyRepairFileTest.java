// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.orders.domain.Order;
import com.exportflow.testing.TaskManagementFixture;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

// [AC-088](../../../../../../../docs/product/acceptance-criteria.md#ac-088)
// [AC-089](../../../../../../../docs/product/acceptance-criteria.md#ac-089)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
class AssemblyRepairFileTest extends TaskManagementFixture {
  @Test
  void TC414_unreadableDatabaseIsUnknownAndNeverTreatedAsMissingRecoveryEvidence()
      throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    probe.on(
        REPAIR,
        "load",
        (method, args, real) -> {
          throw new org.springframework.dao.DataAccessResourceFailureException(
              "synthetic database unavailable");
        });
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("UNKNOWN");
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(probe.calls(ORDERS, "readSegment")).isZero();
    for (var block : data.blocks())
      assertThat((Path) port(STORAGE, "resolve", block.storageRef())).exists();
    assertThat(count("command_outbox")).isEqualTo(1);
  }

  private static final String ASSEMBLER =
      "com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler";
  private static final String VALIDATOR =
      "com.exportflow.worker.infrastructure.excel.ExcelFileValidator";

  @Test
  void TC415_changedSourceContentFailsWithoutChangingProvidedOrdersOrHealthyBlocks()
      throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    var before = db.queryForList("SELECT * FROM orders ORDER BY id");
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    probe.on(
        ORDERS,
        "readSegment",
        (method, args, real) -> {
          var rows = new ArrayList<>((List<Order>) real.run());
          var row = rows.getFirst();
          rows.set(
              0,
              new Order(
                  row.id(),
                  row.orderNo(),
                  row.customerName() + "-synthetic-change",
                  row.customerPhone(),
                  row.status(),
                  row.channel(),
                  row.amount(),
                  row.currency(),
                  row.region(),
                  row.createdAt()));
          return rows;
        });
    var result = generate(task, lease);
    assertThat(probe.calls(ORDERS, "readSegment")).as("仅在内存改变真实分段读取结果，来源数据库保持只读").isPositive();
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code").toString())
        .isEqualTo("DATA_REPAIR_SOURCE_CHANGED");
    assertThat(db.queryForList("SELECT * FROM orders ORDER BY id")).isEqualTo(before);
    for (int index : List.of(0, 2))
      assertThat((Path) port(STORAGE, "resolve", data.blocks().get(index).storageRef())).exists();
  }

  @Test
  void TC414_TC415_missingMiddleBlockDuringAssemblyRebuildsOnlyThatSegment() throws Exception {
    damagedBlockCase(true);
  }

  @Test
  void TC414_TC415_corruptMiddleBlockDuringAssemblyRebuildsOnlyThatSegment() throws Exception {
    damagedBlockCase(false);
  }

  private void damagedBlockCase(boolean missing) throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    assertThat(data.blocks()).hasSize(3);
    var middle = data.blocks().get(1);
    var healthy = List.of(data.blocks().get(0), data.blocks().get(2));
    var injected = new AtomicBoolean();
    probe.on(
        ASSEMBLER,
        "assemble",
        (method, args, real) -> {
          if (injected.compareAndSet(false, true)) {
            Path file = (Path) port(STORAGE, "resolve", middle.storageRef());
            if (missing) Files.delete(file);
            else Files.writeString(file, "broken-test-block");
          }
          return real.run();
        });
    var result = generate(task, lease);
    assertThat(injected).as("必须实际触发装配中的文件破坏").isTrue();
    assertThat(property(result, "kind").toString()).as("同任务自动修复后应返回可采用产物").isEqualTo("READY");
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForObject(
                "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(data.generation());
    var active =
        db.queryForList(
            "SELECT active_block_id FROM export_data_segments WHERE task_id=? ORDER BY sequence",
            String.class,
            task.toString());
    assertThat(active).hasSize(3);
    assertThat(active.get(0)).isEqualTo(healthy.get(0).blockId().toString());
    assertThat(active.get(2)).isEqualTo(healthy.get(1).blockId().toString());
    assertThat(active.get(1)).isNotEqualTo(middle.blockId().toString());
    for (var block : healthy)
      assertThat((Path) port(STORAGE, "resolve", block.storageRef())).exists();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM command_outbox WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(1);
    assertWorkbookMatchesSource(task);
  }

  @Test
  void TC415_missingRegistrationUsesIndependentManifestAndPreservesHealthyGeneration()
      throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    var lost = data.blocks().get(1);
    db.update("DELETE FROM export_data_blocks WHERE block_id=?", lost.blockId().toString());
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(
            db.queryForObject(
                "SELECT data_generation FROM export_checkpoints WHERE task_id=?",
                Long.class,
                task.toString()))
        .as("不得用全量新代重建替代选择性修复")
        .isEqualTo(data.generation());
    assertThat(
            db.queryForObject(
                "SELECT active_block_id FROM export_data_segments WHERE task_id=? AND sequence=1",
                String.class,
                task.toString()))
        .isEqualTo(data.blocks().get(0).blockId().toString());
  }

  @Test
  void TC414_TC415_corruptCandidateWithHealthyBlocksReassemblesAndKeepsBlockIds() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    var injected = new AtomicBoolean();
    probe.on(
        VALIDATOR,
        "validate",
        (method, args, real) -> {
          if (injected.compareAndSet(false, true)) Files.writeString((Path) args[0], "not-an-xlsx");
          return real.run();
        });
    var result = generate(task, lease);
    assertThat(injected).isTrue();
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForList(
                "SELECT active_block_id FROM export_data_segments WHERE task_id=? ORDER BY sequence",
                String.class,
                task.toString()))
        .containsExactlyElementsOf(
            data.blocks().stream().map(block -> block.blockId().toString()).toList());
    assertWorkbookMatchesSource(task);
  }

  @Test
  void TC415_persistentDamageExhaustsThreeDurableRoundsAndNeverPublishesResult() throws Exception {
    var task = create();
    var lease = lease(task);
    probe.on(
        VALIDATOR,
        "validate",
        (method, args, real) -> {
          Files.writeString((Path) args[0], "always-corrupt-test-candidate");
          return real.run();
        });
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code").toString())
        .isEqualTo("DATA_REPAIR_EXHAUSTED");
    assertThat(
            db.queryForObject(
                "SELECT repair_attempt FROM export_tasks WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isEqualTo(3);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                Long.class,
                task.toString()))
        .isZero();
  }

  private void assertWorkbookMatchesSource(UUID task) throws Exception {
    var expected = db.queryForList("SELECT * FROM orders ORDER BY created_at DESC,id DESC");
    var statuses =
        Map.of(
            "PENDING",
            "待支付",
            "PAID",
            "已支付",
            "SHIPPED",
            "已发货",
            "COMPLETED",
            "已完成",
            "CANCELED",
            "已取消");
    var channels = Map.of("WEB", "Web 商城", "APP", "移动 App", "STORE", "线下门店", "PARTNER", "合作渠道");
    var time = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(download(task)))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isEqualTo(expected.size());
      for (int index = 0; index < expected.size(); index++) {
        var row = expected.get(index);
        var value = row.get("created_at");
        var created =
            (value instanceof java.time.LocalDateTime date
                    ? date
                    : ((java.sql.Timestamp) value).toLocalDateTime())
                .plusHours(8);
        var values =
            List.of(
                row.get("order_no").toString(),
                row.get("customer_name").toString(),
                row.get("customer_phone").toString(),
                statuses.getOrDefault(row.get("status").toString(), "未知"),
                channels.getOrDefault(row.get("channel").toString(), "未知"),
                ((java.math.BigDecimal) row.get("amount")).setScale(2).toPlainString(),
                row.get("currency").toString(),
                row.get("region").toString(),
                time.format(created));
        assertThat(sheet.getRow(index + 1).getLastCellNum()).isEqualTo((short) 9);
        for (int column = 0; column < 9; column++) {
          var cell = sheet.getRow(index + 1).getCell(column);
          assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
          assertThat(cell.getStringCellValue())
              .as("行%s列%s", index, column)
              .isEqualTo(values.get(column));
        }
      }
    }
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_task_repairs WHERE task_id=? AND state<>'COMPLETE'",
                Long.class,
                task.toString()))
        .isZero();
  }
}
