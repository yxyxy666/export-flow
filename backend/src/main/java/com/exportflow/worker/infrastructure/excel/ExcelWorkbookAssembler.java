// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[ExcelBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelContentTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelContentTest.java)、[ExcelPerformanceTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelPerformanceTest.java)。
package com.exportflow.worker.infrastructure.excel;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import com.exportflow.worker.port.ExecutionGuard;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.streaming.*;
import org.springframework.stereotype.Component;

/** 每块刷sheet与缓冲，最终只封装一次ZIP；设计 excel-design.md，TC-230/231。 */
@Component
public class ExcelWorkbookAssembler {
  private final ExportFileStorage storage;
  private final ExcelRowProjector projector;

  public ExcelWorkbookAssembler(ExportFileStorage storage, ExcelRowProjector projector) {
    this.storage = storage;
    this.projector = projector;
  }

  public StagingWorkbook assemble(
      GenerationContext context,
      CompletedDataset data,
      AttemptWorkspace workspace,
      ExecutionGuard guard)
      throws IOException {
    UUID identity = UUID.randomUUID();
    var ref =
        new StorageRef(
            "STAGING",
            workspace.taskId(),
            identity,
            data.generation(),
            workspace.fence(),
            workspace.owner());
    Path path = storage.resolve(ref);
    var book = createWorkbook();
    OutputStream output = null;
    Throwable primary = null;
    long written = 0;
    try (var scope = com.exportflow.worker.infrastructure.file.ScratchFileStrategy.use(workspace)) {
      book.setCompressTempFiles(true);
      var sheet = book.createSheet("订单");
      sheet.createFreezePane(0, 1);
      writeRow(sheet, 0, projector.headers(context.snapshot().fields()));
      for (int column = 0; column < context.snapshot().fields().size(); column++)
        sheet.setColumnWidth(column, 24 * 256);
      for (var block : data.blocks()) {
        try (var reader = storage.readBlock(block)) {
          for (var row = reader.next(); row.isPresent(); row = reader.next()) {
            check(guard);
            writeRow(
                sheet,
                Math.toIntExact(++written),
                projector.cells(row.get(), context.snapshot().fields()));
          }
        }
        flushCompletedRows(sheet, guard, 0);
      }
      if (written != data.totalRows())
        throw new InvalidExportDataException("EXPORT_SOURCE_CHANGED");
      check(guard);
      output =
          new BufferedOutputStream(
              Files.newOutputStream(path, StandardOpenOption.CREATE_NEW), 65536);
      book.write(output);
      output.flush();
    } catch (IOException | RuntimeException failure) {
      primary = failure;
      throw failure;
    } finally {
      try {
        closeWorkbook(book, output);
      } catch (IOException | RuntimeException closeFailure) {
        if (primary != null) primary.addSuppressed(closeFailure);
        else throw closeFailure;
      }
    }
    try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
    return new StagingWorkbook(ref, identity, written, projector.fileName(context.snapshot()));
  }

  private void writeRow(SXSSFSheet sheet, int number, List<String> cells) {
    var row = sheet.createRow(number);
    for (int column = 0; column < cells.size(); column++)
      row.createCell(column, CellType.STRING).setCellValue(cells.get(column));
  }

  /** 使用POI受支持的writer扩展点，压缩流SYNC_FLUSH后本批数据真正可从磁盘解码。 */
  public SXSSFWorkbook createWorkbook() {
    return new SXSSFWorkbook(null, 100, true, false) {
      @Override
      protected SheetDataWriter createSheetDataWriter() throws IOException {
        return new GZIPSheetDataWriter() {
          @Override
          protected OutputStream decorateOutputStream(FileOutputStream output) throws IOException {
            return new java.util.zip.GZIPOutputStream(output, 65536, true);
          }
        };
      }
    };
  }

  public void flushCompletedRows(SXSSFSheet sheet, ExecutionGuard guard, int retainedRows)
      throws IOException {
    check(guard);
    sheet.flushRows(retainedRows);
    sheet.flushBufferedData();
  }

  public void closeWorkbook(SXSSFWorkbook workbook, OutputStream output) throws IOException {
    Throwable failure = null;
    try {
      if (output != null) output.close();
    } catch (IOException | RuntimeException error) {
      failure = error;
    }
    // POI 5.5 close() 自带dispose；先检查首次清理结果，避免把第二次返回false误判为失败。
    try {
      if (!workbook.dispose()) throw new IOException("临时资源关闭未确认");
    } catch (RuntimeException | IOException error) {
      if (failure == null) failure = error;
      else failure.addSuppressed(error);
    }
    try {
      workbook.close();
    } catch (IOException | RuntimeException error) {
      if (failure == null) failure = error;
      else failure.addSuppressed(error);
    }
    if (failure instanceof IOException io) throw io;
    if (failure instanceof RuntimeException runtime) throw runtime;
  }

  private void check(ExecutionGuard guard) throws IOException {
    if (!guard.check() || Thread.currentThread().isInterrupted()) throw new IOException("执行已停止");
  }
}
