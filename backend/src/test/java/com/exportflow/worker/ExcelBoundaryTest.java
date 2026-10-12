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
import org.apache.poi.xssf.streaming.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
// [SC-005](../../../../../../../docs/product/acceptance-criteria.md#sc-005)
// [SC-007](../../../../../../../docs/product/acceptance-criteria.md#sc-007)
class ExcelBoundaryTest extends BoundaryFixture {
  private static final String ASSEMBLER =
      "com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler";

  @ParameterizedTest
  @ValueSource(strings = {"overlong", "control", "negative-prefix", "max-money"})
  void TC233_projectorRejectsInvalidCellsAndKeepsValidOriginalText(String scenario)
      throws Exception {
    var response =
        post(
            "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"]},\"fields\":[\"customerName\",\"amount\"]}",
            newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    var task = UUID.fromString(body(response).get("taskId").asText());
    var snapshot = snapshot(task);
    var batch = port(ORDERS, "readBatch", task, property(snapshot, "scope"), null, 1000);
    Object order = ((List<?>) property(batch, "rows")).getFirst();
    Map<String, Object> changes = new HashMap<>();
    changes.put(
        "customerName",
        scenario.equals("overlong")
            ? "中".repeat(32768)
            : scenario.equals("control") ? "合成\u0001客户" : "-合成原文");
    changes.put("amount", new java.math.BigDecimal("9999999999999999.99"));
    Object fields = property(snapshot, "fields");
    if (scenario.equals("overlong") || scenario.equals("control"))
      assertThatThrownBy(
              () ->
                  call(
                      "com.exportflow.worker.infrastructure.excel.ExcelRowProjector",
                      "cells",
                      copy(order, changes),
                      fields))
          .isInstanceOf(Exception.class);
    else
      assertThat(
              call(
                  "com.exportflow.worker.infrastructure.excel.ExcelRowProjector",
                  "cells",
                  copy(order, changes),
                  fields))
          .isEqualTo(List.of("-合成原文", "9999999999999999.99"));
  }

  @Test
  void TC231_batchFlushReallyWritesCompletedRowsToScratchAndReleasesWindow() throws Exception {
    try (var workbook = (SXSSFWorkbook) call(ASSEMBLER, "createWorkbook")) {
      workbook.setCompressTempFiles(true);
      var sheet = workbook.createSheet("订单");
      for (int i = 0; i < 1000; i++)
        sheet.createRow(i).createCell(0).setCellValue("synthetic-row-" + i);
      call(ASSEMBLER, "flushCompletedRows", sheet, guard(true), 0);
      assertThat(sheet.getLastFlushedRowNum()).isEqualTo(999);
      assertThat((Object) sheet.getRow(999)).isNull();
      var getWriter = SXSSFSheet.class.getDeclaredMethod("getSheetDataWriter");
      getWriter.setAccessible(true);
      Object writer = getWriter.invoke(sheet);
      java.lang.reflect.Method getFile = null;
      for (Class<?> t = writer.getClass(); t != null && getFile == null; t = t.getSuperclass())
        try {
          getFile = t.getDeclaredMethod("getTempFile");
        } catch (NoSuchMethodException ignored) {
        }
      java.util.Objects.requireNonNull(getFile).setAccessible(true);
      Path scratch = ((File) getFile.invoke(writer)).toPath();
      assertThat(Files.size(scratch)).isPositive();
      // 组装中的gzip尚无最终trailer，核对当前可解码的已刷出行，不把它当完成文件。
      var decoded = new ByteArrayOutputStream();
      try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(scratch))) {
        var bytes = new byte[4096];
        try {
          int n;
          while ((n = in.read(bytes)) != -1) decoded.write(bytes, 0, n);
        } catch (EOFException unfinished) {
          /* 只允许未关闭流缺少trailer；内容断言仍完整。 */
        }
      }
      assertThat(decoded.toString(java.nio.charset.StandardCharsets.UTF_8))
          .contains("synthetic-row-0", "synthetic-row-999");
      workbook.close();
      workbook.dispose();
      assertThat(scratch).doesNotExist();
    }
  }

  @Test
  void TC231_flushFailureAndLostGuardStopBeforeBufferedWrite() throws Exception {
    try (var workbook = new SXSSFWorkbook(100)) {
      var sheet = spy(workbook.createSheet("订单"));
      doThrow(new IOException("synthetic flush failure")).when(sheet).flushRows(0);
      assertThatThrownBy(() -> call(ASSEMBLER, "flushCompletedRows", sheet, guard(true), 0))
          .hasCauseInstanceOf(IOException.class);
      verify(sheet, never()).flushBufferedData();
      clearInvocations(sheet);
      assertThatThrownBy(() -> call(ASSEMBLER, "flushCompletedRows", sheet, guard(false), 0))
          .isInstanceOf(Exception.class);
      verify(sheet, never()).flushRows(anyInt());
      verify(sheet, never()).flushBufferedData();
    }
  }

  @Test
  void TC231_outputCloseFailureStillDisposesOwnScratch() throws Exception {
    var workbook = spy(new SXSSFWorkbook(100));
    var output = mock(OutputStream.class);
    doThrow(new IOException("synthetic close")).when(output).close();
    try {
      call(ASSEMBLER, "closeWorkbook", workbook, output);
    } catch (java.lang.reflect.InvocationTargetException expected) {
      assertThat(expected.getCause()).isNotNull();
    }
    verify(output).close();
    verify(workbook).close();
    verify(workbook, atLeastOnce()).dispose();
  }

  @Test
  void TC230_existingArtifactIsNeverOverwrittenAndFailureDoesNotRegisterReady() throws Exception {
    var task = create();
    var lease = lease(task);
    AtomicReference<Path> target = new AtomicReference<>();
    probe.on(
        STORAGE,
        "publish",
        (m, a, real) -> {
          Path path =
              root.resolve("tasks")
                  .resolve(task.toString())
                  .resolve("artifacts")
                  .resolve(property(a[1], "artifactId") + ".xlsx");
          Files.createDirectories(path.getParent());
          Files.writeString(path, "synthetic prior immutable artifact");
          target.set(path);
          return real.run();
        });
    var result = generate(task, lease);
    assertThat(target.get()).isNotNull();
    assertThat(property(result, "kind").toString()).isNotEqualTo("READY");
    assertThat(Files.readString(target.get())).isEqualTo("synthetic prior immutable artifact");
    assertThat(count("export_artifacts")).isZero();
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"publish", "writeBlock"})
  void TC230_storageFaultNeverMakesHalfFileDownloadable(String operation) throws Exception {
    var task = create();
    var lease = lease(task);
    var reached = new AtomicBoolean();
    probe.on(
        STORAGE,
        operation,
        (m, a, real) -> {
          reached.set(true);
          throw new FileSystemException("synthetic", "synthetic", "No space left on device");
        });
    var result = generate(task, lease);
    assertThat(reached).isTrue();
    assertThat(property(result, "kind").toString()).isNotEqualTo("READY");
    assertThat(count("export_artifacts")).isZero();
    assertThat(get("/api/v1/export-tasks/" + task + "/file").statusCode()).isEqualTo(409);
  }

  @Test
  void TC232_memoryRowsAreNotProgressAndCollectedRowsStay99UntilSuccessFailureKeepsCount()
      throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    var checked = new AtomicBoolean();
    probe.on(
        CHECKPOINT,
        "commitBlock",
        (m, a, real) -> {
          if (checked.compareAndSet(false, true))
            assertThat(
                    db.queryForObject(
                        "SELECT processed_count FROM export_tasks WHERE task_id=?",
                        Long.class,
                        task.toString()))
                .isZero();
          return real.run();
        });
    var ready = generate(task, lease);
    assertThat(checked).isTrue();
    Object summary = ((Optional<?>) port(TASKS, "findSummary", task)).orElseThrow();
    assertThat(property(summary, "progressPercent")).isEqualTo(99);
    assertThat(call(EXEC_SERVICE, "complete", lease, failure("EXPORT_FILE_INVALID")).toString())
        .isEqualTo("APPLIED");
    summary = ((Optional<?>) port(TASKS, "findSummary", task)).orElseThrow();
    assertThat(((Number) property(summary, "processedCount")).longValue()).isEqualTo(2501);
    assertThat(property(summary, "progressPercent")).isEqualTo(99);
    assertThat(state(task)).isEqualTo("FAILED");
    assertThat(
            db.queryForObject(
                "SELECT error_code FROM export_tasks WHERE task_id=?",
                String.class,
                task.toString()))
        .isEqualTo("EXPORT_FILE_INVALID");
    assertThat(
            db.queryForObject(
                "SELECT finished_at FROM export_tasks WHERE task_id=?",
                java.sql.Timestamp.class,
                task.toString()))
        .isNotNull();
  }

  @Test
  void TC233_selectedKeysetBatchesAcrossTimesHaveExactSourceAndReleaseTransactions()
      throws Exception {
    useOrderVolume(2501);
    var ids = new ArrayList<String>();
    for (int id = 1; id <= 2501; id++) ids.add(Integer.toString(id));
    var response =
        post(
            json.writeValueAsString(
                Map.of(
                    "scope",
                    Map.of("type", "SELECTED", "orderIds", ids),
                    "fields",
                    List.of("orderNo"))),
            newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    var task = UUID.fromString(body(response).get("taskId").asText());
    Object scope = property(snapshot(task), "scope"), cursor = null;
    var actual = new ArrayList<Long>();
    for (int n = 0; n < 10; n++) {
      Object batch = port(ORDERS, "readBatch", task, scope, cursor, 1000);
      var rows = (List<?>) property(batch, "rows");
      assertThat(rows).hasSizeLessThanOrEqualTo(1000);
      assertThat(
              org.springframework.transaction.support.TransactionSynchronizationManager
                  .isActualTransactionActive())
          .isFalse();
      for (Object row : rows) actual.add(((Number) property(row, "id")).longValue());
      if ((boolean) property(batch, "isEnd")) break;
      cursor = property(batch, "lastCursor");
    }
    assertThat(actual)
        .containsExactlyElementsOf(
            db.queryForList("SELECT id FROM orders ORDER BY created_at DESC,id DESC", Long.class));
  }

  @Test
  void TC230_longFilePipelineNeverCarriesDatabaseTransactionOrBorrowedConnection()
      throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    var reached = new java.util.HashSet<String>();
    for (String operation : List.of("writeBlock", "readBlock", "publish"))
      probe.on(
          STORAGE,
          operation,
          (m, a, real) -> {
            reached.add(operation);
            assertThat(
                    org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive())
                .isFalse();
            var source = app.getBean(javax.sql.DataSource.class);
            var pool = source.unwrap(com.zaxxer.hikari.HikariDataSource.class);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections())
                .as("文件IO前批读连接应已归还")
                .isZero();
            return real.run();
          });
    assertThat(property(generate(task, lease), "kind").toString()).isEqualTo("READY");
    assertThat(reached).containsExactlyInAnyOrder("writeBlock", "readBlock", "publish");
  }
}
