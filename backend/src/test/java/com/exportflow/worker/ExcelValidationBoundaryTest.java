// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.exportflow.testing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
// [SC-007](../../../../../../../docs/product/acceptance-criteria.md#sc-007)
class ExcelValidationBoundaryTest extends BoundaryFixture {
  private static final String ASSEMBLER =
      "com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler";
  private static final String CODEC = "com.exportflow.worker.infrastructure.file.DataBlockCodec";

  @ParameterizedTest
  @ValueSource(strings = {"zip", "header", "columns", "rows", "formula", "numeric", "extra-sheet"})
  void TC233_realCorruptWorkbookNeverRegistersReady(String defect) throws Exception {
    var task = create();
    var lease = lease(task);
    var reached = new AtomicBoolean();
    probe.on(
        ASSEMBLER,
        "assemble",
        (m, a, real) -> {
          Object result = real.run();
          Path path = (Path) port(STORAGE, "resolve", property(result, "ref"));
          reached.set(true);
          if (defect.equals("zip")) {
            Files.writeString(path, "synthetic corrupt ZIP");
            return result;
          }
          // 测试仅修改小夹具；正式校验器必须仍以SAX有界读取。
          try (var input = Files.newInputStream(path);
              var workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheetAt(0);
            var first = sheet.getRow(1);
            switch (defect) {
              case "header" -> sheet.getRow(0).getCell(0).setCellValue("错误表头");
              case "columns" -> first.removeCell(first.getCell(0));
              case "rows" -> sheet.removeRow(first);
              case "formula" -> first.getCell(0).setCellFormula("1+1");
              case "numeric" -> first.getCell(0).setCellValue(12.34);
              case "extra-sheet" -> workbook.createSheet("多余工作表");
            }
            try (var output = Files.newOutputStream(path)) {
              workbook.write(output);
            }
          }
          return result;
        });
    var result = generate(task, lease);
    assertThat(reached).isTrue();
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code")).isEqualTo("EXPORT_FILE_INVALID");
    assertThat(count("export_artifacts")).isZero();
    assertThat(get("/api/v1/export-tasks/" + task + "/file").statusCode()).isEqualTo(409);
    try (var files = Files.walk(root.resolve("scratch"))) {
      assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"force", "atomic-move"})
  void TC231_persistenceFaultPreservesTrustedBlocksAndCannotExposePartialFile(String fault)
      throws Exception {
    var task = create();
    var lease = lease(task);
    var reached = new AtomicBoolean();
    probe.on(
        STORAGE,
        "publish",
        (m, a, real) -> {
          reached.set(true);
          if (fault.equals("force")) throw new SyncFailedException("synthetic force failed");
          throw new AtomicMoveNotSupportedException(
              "synthetic-part", "synthetic-final", "synthetic same-volume move failure");
        });
    var result = generate(task, lease);
    assertThat(reached).isTrue();
    assertThat(property(result, "kind").toString()).isNotEqualTo("READY");
    assertThat(count("export_artifacts")).isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isPositive();
    try (var files = Files.walk(root.resolve("scratch"))) {
      assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"close", "dispose"})
  void TC231_workbookResourceFailureStillAttemptsAllCloses(String operation) throws Exception {
    var workbook = spy(new org.apache.poi.xssf.streaming.SXSSFWorkbook(100));
    var output = mock(OutputStream.class);
    if (operation.equals("close"))
      doThrow(new IOException("synthetic close failed")).when(workbook).close();
    else doThrow(new IllegalStateException("synthetic dispose failed")).when(workbook).dispose();
    try {
      assertThatThrownBy(() -> call(ASSEMBLER, "closeWorkbook", workbook, output))
          .isInstanceOf(Exception.class);
      verify(output).close();
      verify(workbook).close();
      verify(workbook, atLeastOnce()).dispose();
    } finally {
      reset(workbook);
      workbook.close();
      workbook.dispose();
    }
  }

  @Test
  void TC230_sourceCountChangedAfterAcceptanceFailsSafely() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    useOrderVolume(2500); // 仅缩小测试视图，模拟来源范围变小；不删除负责人订单。
    var lease = lease(task);
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code")).isEqualTo("EXPORT_SOURCE_CHANGED");
    assertThat(count("export_artifacts")).isZero();
    assertThat(call(EXEC_SERVICE, "complete", lease, result).toString()).isEqualTo("APPLIED");
    assertThat(state(task)).isEqualTo("FAILED");
  }

  @Test
  void TC235_blockCodecEnforcesExactUtf8LineBudgetAndWholeBlockBudget() throws Exception {
    var response =
        post(
            "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"]},\"fields\":[\"orderNo\"]}",
            newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    var task = UUID.fromString(body(response).get("taskId").asText());
    var lease = lease(task);
    var captured = new AtomicReference<Object[]>();
    probe.on(
        STORAGE,
        "writeBlock",
        (m, a, real) -> {
          captured.compareAndSet(null, a.clone());
          return real.run();
        });
    generate(task, lease);
    probe.clear();
    assertThat(captured.get()).isNotNull();
    Object header = captured.get()[1], order = ((List<?>) captured.get()[2]).getFirst();
    var output = new ByteArrayOutputStream();
    Object shortOrder = copy(order, Map.of("customerName", "a"));
    call(CODEC, "write", header, List.of(shortOrder), output);
    byte[] row = output.toByteArray();
    int firstNewline = -1;
    for (int i = 0; i < row.length; i++)
      if (row[i] == '\n') {
        firstNewline = i;
        break;
      }
    assertThat(firstNewline).isPositive();
    int lineBytes = row.length - firstNewline - 1;
    if (row[row.length - 1] == '\n') lineBytes--; // 行预算不含分隔换行。
    int nameAtLimit = 1024 * 1024 - (lineBytes - 1);
    assertThat(nameAtLimit).isPositive();
    Object exact = copy(order, Map.of("customerName", "a".repeat(nameAtLimit)));
    call(CODEC, "write", header, List.of(exact), OutputStream.nullOutputStream());
    Object over = copy(order, Map.of("customerName", "a".repeat(nameAtLimit + 1)));
    assertThatThrownBy(
            () -> call(CODEC, "write", header, List.of(over), OutputStream.nullOutputStream()))
        .isInstanceOf(Exception.class);
    List<Object> bounded = new ArrayList<>();
    for (long id = 1000; id >= 1; id--)
      bounded.add(
          copy(
              order,
              Map.of(
                  "id",
                  id,
                  "orderNo",
                  "SYNTHETIC-BUDGET-" + id,
                  "customerName",
                  "中".repeat(8000))));
    assertThatThrownBy(
            () ->
                call(
                    CODEC,
                    "write",
                    copy(header, Map.of("rowCount", 1000)),
                    bounded,
                    OutputStream.nullOutputStream()))
        .isInstanceOf(Exception.class);
  }
}
