package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;

// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
// 规格：[Excel方案](../../../../../../../specs/002-order-management/excel-design.md)
class WorkbookLifecycleTest {
  @Test
  void TC231_poi55NormalCloseDoesNotMisclassifyAlreadyDisposedScratchAsFailure() throws Exception {
    var assembler = new ExcelWorkbookAssembler(null, null);
    var output = new ByteArrayOutputStream();
    var workbook = new SXSSFWorkbook(100);
    workbook.createSheet("订单").createRow(0).createCell(0).setCellValue("合成数据");
    workbook.write(output);
    assertThatCode(() -> assembler.closeWorkbook(workbook, output)).doesNotThrowAnyException();
    assertThat(output.size()).isPositive();
  }
}
