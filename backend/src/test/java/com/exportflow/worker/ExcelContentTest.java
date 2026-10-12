// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
// [SC-007](../../../../../../../docs/product/acceptance-criteria.md#sc-007)
class ExcelContentTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @ParameterizedTest
  @ValueSource(ints = {110000, 200000})
  void TC230_TC233_fullVolumeNineColumnsMatchEveryProvidedSourceRow(int count) throws Exception {
    useOrderVolume(count);
    var task = createVolume();
    awaitSuccess(task);
    Path path = root.resolve("volume-accuracy.xlsx");
    Files.write(path, download(task));
    var seen = new java.util.concurrent.atomic.AtomicInteger();
    try (var connection = db.getDataSource().getConnection();
        var statement =
            connection.prepareStatement(
                "SELECT * FROM orders ORDER BY created_at DESC,id DESC",
                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                java.sql.ResultSet.CONCUR_READ_ONLY)) {
      statement.setFetchSize(Integer.MIN_VALUE);
      try (var expectedRows = statement.executeQuery();
          var container = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
        var reader = new XSSFReader(container);
        var sheets = reader.getSheetsData();
        assertThat(sheets.hasNext()).isTrue();
        try (var stream = sheets.next()) {
          var parser = org.apache.poi.util.XMLHelper.newXMLReader();
          parser.setContentHandler(
              new XSSFSheetXMLHandler(
                  reader.getStylesTable(),
                  null,
                  reader.getSharedStringsTable(),
                  new XSSFSheetXMLHandler.SheetContentsHandler() {
                    private List<String> row;

                    public void startRow(int number) {
                      row = new ArrayList<>();
                    }

                    public void cell(String reference, String value, XSSFComment comment) {
                      row.add(value);
                    }

                    public void endRow(int number) {
                      if (number == 0) {
                        assertThat(row)
                            .containsExactly(
                                "订单号", "客户姓名", "客户手机号", "状态", "渠道", "金额", "币种", "地区", "下单时间");
                      } else {
                        seen.incrementAndGet();
                        List<String> expected;
                        try {
                          assertThat(expectedRows.next()).as("来源行数不少于文件行数").isTrue();
                          expected =
                              List.of(
                                  expectedRows.getString("order_no"),
                                  expectedRows.getString("customer_name"),
                                  expectedRows.getString("customer_phone"),
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
                                          "已取消")
                                      .getOrDefault(expectedRows.getString("status"), "未知"),
                                  Map.of(
                                          "WEB", "Web 商城", "APP", "移动 App", "STORE", "线下门店",
                                          "PARTNER", "合作渠道")
                                      .getOrDefault(expectedRows.getString("channel"), "未知"),
                                  expectedRows.getBigDecimal("amount").setScale(2).toPlainString(),
                                  expectedRows.getString("currency"),
                                  expectedRows.getString("region"),
                                  java.time.format.DateTimeFormatter.ofPattern(
                                          "yyyy-MM-dd HH:mm:ss")
                                      .withZone(java.time.ZoneId.of("Asia/Hong_Kong"))
                                      .format(expectedRows.getTimestamp("created_at").toInstant()));
                        } catch (java.sql.SQLException failure) {
                          throw new AssertionError("只读来源核对失败", failure);
                        }
                        assertThat(row.equals(expected))
                            .as("来源与文件第%s行不一致（不输出客户内容）", number)
                            .isTrue();
                      }
                    }
                  },
                  new DataFormatter(Locale.ROOT),
                  false));
          parser.parse(new org.xml.sax.InputSource(stream));
        }
        assertThat(sheets.hasNext()).isFalse();
        assertThat(expectedRows.next()).as("来源没有遗漏行").isFalse();
      }
    }
    assertThat(seen).hasValue(count);
    assertThat(
            db.queryForObject(
                "SELECT processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(count);
  }

  @Test
  void TC233_unknownEnumsAndFormulaPrefixesUseOnlyInMemoryBoundaryInputs() throws Exception {
    var projector = app.getBean(com.exportflow.worker.infrastructure.excel.ExcelRowProjector.class);
    var source =
        app.getBean(com.exportflow.orders.port.OrderRepository.class)
            .findPage(
                new com.exportflow.orders.domain.OrderQuery(
                    new com.exportflow.orders.domain.OrderQuery.OrderFilter(
                        null, null, null, null, null, null, null, null),
                    1,
                    20,
                    "ASC"))
            .getFirst();
    var input =
        new com.exportflow.orders.domain.Order(
            source.id(),
            source.orderNo(),
            "+边界输入",
            "@001",
            "FUTURE_STATUS",
            "FUTURE_CHANNEL",
            source.amount(),
            source.currency(),
            source.region(),
            source.createdAt());
    var fields =
        List.of(
            com.exportflow.exporttasks.domain.ExportField.customerName,
            com.exportflow.exporttasks.domain.ExportField.customerPhone,
            com.exportflow.exporttasks.domain.ExportField.status,
            com.exportflow.exporttasks.domain.ExportField.channel);
    assertThat(projector.cells(input, fields)).containsExactly("+边界输入", "@001", "未知", "未知");
    assertThat(
            db.queryForObject(
                "SELECT customer_name FROM orders WHERE id=?", String.class, source.id()))
        .isEqualTo(source.customerName());
  }

  @Test
  void TC230_TC233_realWorkbookHasOneSheetExactRowsOrderedFieldsAndOriginalText() throws Exception {
    var request =
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"]},\"fields\":[\"customerName\",\"customerPhone\",\"amount\",\"createdAt\"]}";
    var response = post(request, newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    var task = UUID.fromString(body(response).get("taskId").asText());
    awaitSuccess(task);
    var path = root.resolve("download-test.xlsx");
    Files.write(path, download(task));
    var rows = new ArrayList<List<String>>();
    try (var container = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
      var reader = new XSSFReader(container);
      var sheets = reader.getSheetsData();
      assertThat(sheets.hasNext()).isTrue();
      try (var sheet = sheets.next()) {
        var parser = org.apache.poi.util.XMLHelper.newXMLReader();
        parser.setContentHandler(
            new XSSFSheetXMLHandler(
                reader.getStylesTable(),
                null,
                reader.getSharedStringsTable(),
                new XSSFSheetXMLHandler.SheetContentsHandler() {
                  private List<String> row;

                  public void startRow(int number) {
                    row = new ArrayList<>();
                  }

                  public void endRow(int number) {
                    rows.add(row);
                  }

                  public void cell(String reference, String value, XSSFComment comment) {
                    row.add(value);
                  }
                },
                new DataFormatter(Locale.ROOT),
                false));
        parser.parse(new org.xml.sax.InputSource(sheet));
      }
      assertThat(sheets.hasNext()).isFalse();
    }
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(path.toFile())) {
      assertThat(book.getNumberOfSheets()).isEqualTo(1);
      assertThat(book.getSheetName(0)).isEqualTo("订单");
      for (var row : book.getSheetAt(0))
        for (var cell : row)
          assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
    }
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsExactly("客户姓名", "客户手机号", "金额", "下单时间");
    var original = db.queryForMap("SELECT * FROM orders WHERE id=1");
    var expected =
        List.of(
            original.get("customer_name").toString(),
            original.get("customer_phone").toString(),
            ((java.math.BigDecimal) original.get("amount")).setScale(2).toPlainString(),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("Asia/Hong_Kong"))
                .format(
                    ((java.time.LocalDateTime) original.get("created_at"))
                        .toInstant(java.time.ZoneOffset.UTC)));
    assertThat(rows.get(1).equals(expected)).as("文件字段与负责人原始订单一致").isTrue();
    // XML直接验证原值为字符串而非公式，防止DataFormatter掩盖类型错误。
    try (var zip = new java.util.zip.ZipFile(path.toFile())) {
      var xml =
          new String(
              zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
      assertThat(xml).doesNotContain("<f>", "<f ");
      assertThat(xml).contains("t=\"inlineStr\"");
    }
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                Long.class,
                task.toString()))
        .isEqualTo(1);
  }

  static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> enumCases() {
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
    return statuses.entrySet().stream()
        .flatMap(
            status ->
                channels.entrySet().stream()
                    .map(
                        channel ->
                            org.junit.jupiter.params.provider.Arguments.of(
                                status.getKey(),
                                status.getValue(),
                                channel.getKey(),
                                channel.getValue())));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("enumCases")
  void TC233_allKnownEnumDisplaysAreExactStrings(
      String status, String statusLabel, String channel, String channelLabel) throws Exception {
    var projector = app.getBean(com.exportflow.worker.infrastructure.excel.ExcelRowProjector.class);
    var source =
        app.getBean(com.exportflow.orders.port.OrderRepository.class)
            .findPage(
                new com.exportflow.orders.domain.OrderQuery(
                    new com.exportflow.orders.domain.OrderQuery.OrderFilter(
                        null, null, null, null, null, null, null, null),
                    1,
                    20,
                    "ASC"))
            .getFirst();
    var input =
        new com.exportflow.orders.domain.Order(
            source.id(),
            source.orderNo(),
            source.customerName(),
            source.customerPhone(),
            status,
            channel,
            source.amount(),
            source.currency(),
            source.region(),
            source.createdAt());
    assertThat(
            projector.cells(
                input,
                List.of(
                    com.exportflow.exporttasks.domain.ExportField.status,
                    com.exportflow.exporttasks.domain.ExportField.channel)))
        .containsExactly(statusLabel, channelLabel);
  }
}
