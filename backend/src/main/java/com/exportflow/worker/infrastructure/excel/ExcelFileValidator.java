// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[ExcelBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelContentTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelContentTest.java)、[ExcelValidationBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java)。
package com.exportflow.worker.infrastructure.excel;

import com.exportflow.exporttasks.domain.ExportField;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskSnapshot;
import com.exportflow.worker.port.ExecutionGuard;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.springframework.stereotype.Component;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** SAX逐行验证结构、文本类型和格式，再流式SHA；设计 excel-design.md，TC-233/235。 */
@Component
public class ExcelFileValidator {
  private final ExcelRowProjector projector;

  public ExcelFileValidator(ExcelRowProjector projector) {
    this.projector = projector;
  }

  public ValidatedWorkbook validate(
      Path path, StorageRef ref, TaskSnapshot snapshot, ExecutionGuard guard) throws IOException {
    long rows;
    check(guard);
    try (var container = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
      var reader = new XSSFReader(container);
      rows = validateRows(reader, snapshot.fields(), snapshot.task().totalCount(), guard);
    } catch (InvalidExportDataException known) {
      throw known;
    } catch (org.xml.sax.SAXException malformed) {
      if (malformed.getCause() instanceof IOException stopped) throw stopped;
      throw invalid();
    } catch (org.apache.poi.openxml4j.exceptions.OpenXML4JException
        | javax.xml.parsers.ParserConfigurationException malformed) {
      throw invalid();
    } catch (java.nio.file.AccessDeniedException unavailable) {
      throw unavailable;
    } catch (java.io.FileNotFoundException missing) {
      throw missing;
    } catch (RuntimeException malformed) {
      throw invalid();
    }
    var hash = digest();
    long size = 0;
    byte[] buffer = new byte[65536];
    try (var input = Files.newInputStream(path)) {
      int n;
      while ((n = input.read(buffer)) != -1) {
        check(guard);
        hash.update(buffer, 0, n);
        size += n;
      }
    }
    if (size <= 0) throw invalid();
    return new ValidatedWorkbook(
        ref, size, HexFormat.of().formatHex(hash.digest()), rows, snapshot.configurationHash());
  }

  public long validateRows(
      XSSFReader reader, List<ExportField> fields, long expected, ExecutionGuard guard)
      throws IOException,
          org.apache.poi.openxml4j.exceptions.InvalidFormatException,
          SAXException,
          javax.xml.parsers.ParserConfigurationException {
    var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
    if (!sheets.hasNext()) throw invalid();
    long count;
    try (var stream = sheets.next()) {
      if (!"订单".equals(sheets.getSheetName())) throw invalid();
      var handler = new SheetHandler(fields, expected, guard, reader.getSharedStringsTable());
      var parser = org.apache.poi.util.XMLHelper.newXMLReader();
      parser.setContentHandler(handler);
      parser.parse(new InputSource(stream));
      count = handler.businessRows();
    }
    if (sheets.hasNext() || count != expected) throw invalid();
    return count;
  }

  private MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("摘要能力不可用");
    }
  }

  private InvalidExportDataException invalid() {
    return new InvalidExportDataException("EXPORT_FILE_INVALID");
  }

  private void check(ExecutionGuard guard) throws IOException {
    if (!guard.check() || Thread.currentThread().isInterrupted()) throw new IOException("执行已停止");
  }

  private final class SheetHandler extends DefaultHandler {
    private final List<ExportField> fields;
    private final long expected;
    private final ExecutionGuard guard;
    private final org.apache.poi.xssf.model.SharedStrings shared;
    private int rowNumber = -1, column;
    private String type;
    private StringBuilder value;
    private List<String> cells;
    private boolean collect;

    SheetHandler(
        List<ExportField> fields,
        long expected,
        ExecutionGuard guard,
        org.apache.poi.xssf.model.SharedStrings shared) {
      this.fields = fields;
      this.expected = expected;
      this.guard = guard;
      this.shared = shared;
    }

    public void startElement(String uri, String local, String name, Attributes attributes)
        throws SAXException {
      switch (name) {
        case "row" -> {
          try {
            check(guard);
          } catch (IOException stopped) {
            throw new SAXException(stopped);
          }
          if (++rowNumber > expected || Integer.parseInt(attributes.getValue("r")) != rowNumber + 1)
            throw invalid();
          cells = new ArrayList<>();
          column = 0;
        }
        case "c" -> {
          String reference = attributes.getValue("r");
          var cell = new CellReference(reference);
          if (cell.getRow() != rowNumber || cell.getCol() != column++) throw invalid();
          type = attributes.getValue("t");
          if (!"inlineStr".equals(type) && !"s".equals(type)) throw invalid();
          value = new StringBuilder();
        }
        case "f" -> throw invalid();
        case "t", "v" -> collect = true;
        default -> {}
      }
    }

    public void characters(char[] chars, int start, int length) {
      if (collect) {
        if (value == null || value.length() + length > 32767) throw invalid();
        value.append(chars, start, length);
      }
    }

    public void endElement(String uri, String local, String name) {
      switch (name) {
        case "t", "v" -> collect = false;
        case "c" -> {
          String text = value.toString();
          if ("s".equals(type)) {
            if (shared == null) throw invalid();
            text = shared.getItemAt(Integer.parseInt(text)).getString();
          }
          if (cells == null || cells.size() >= fields.size()) throw invalid();
          cells.add(text);
        }
        case "row" -> {
          if (cells.size() != fields.size()) throw invalid();
          if (rowNumber == 0) {
            if (!cells.equals(projector.headers(fields))) throw invalid();
          } else
            for (int column = 0; column < fields.size(); column++)
              validateValue(fields.get(column), cells.get(column));
        }
        default -> {}
      }
    }

    private void validateValue(ExportField field, String text) {
      if (field == ExportField.amount && !text.matches("[0-9]{1,16}\\.[0-9]{2}")) throw invalid();
      if (field == ExportField.currency && !"CNY".equals(text)) throw invalid();
      if (field == ExportField.createdAt) {
        try {
          java.time.LocalDateTime.parse(
              text,
              java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                  .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (java.time.format.DateTimeParseException bad) {
          throw invalid();
        }
      }
      if (field == ExportField.status
          && !Set.of("待支付", "已支付", "已发货", "已完成", "已取消", "未知").contains(text)) throw invalid();
      if (field == ExportField.channel
          && !Set.of("Web 商城", "移动 App", "线下门店", "合作渠道", "未知").contains(text)) throw invalid();
    }

    long businessRows() {
      return rowNumber;
    }
  }
}
