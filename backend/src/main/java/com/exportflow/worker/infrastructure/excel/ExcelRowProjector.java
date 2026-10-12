// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[ExcelBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelContentTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelContentTest.java)、[ExcelValidationBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java)。
package com.exportflow.worker.infrastructure.excel;

import com.exportflow.exporttasks.domain.ExportField;
import com.exportflow.exporttasks.domain.ExportFileModels.InvalidExportDataException;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskSnapshot;
import com.exportflow.orders.domain.Order;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

/** 金额/手机号/公式前缀均保留为文本；设计 excel-design.md，TC-233。 */
@Component
public class ExcelRowProjector {
  private static final Map<String, String> STATUS =
      Map.of(
          "PENDING", "待支付", "PAID", "已支付", "SHIPPED", "已发货", "COMPLETED", "已完成", "CANCELED", "已取消");
  private static final Map<String, String> CHANNEL =
      Map.of("WEB", "Web 商城", "APP", "移动 App", "STORE", "线下门店", "PARTNER", "合作渠道");
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Hong_Kong"));

  public List<String> headers(List<ExportField> fields) {
    ExportField.validateFields(fields);
    return fields.stream()
        .map(
            field ->
                switch (field) {
                  case orderNo -> "订单号";
                  case customerName -> "客户姓名";
                  case customerPhone -> "客户手机号";
                  case status -> "状态";
                  case channel -> "渠道";
                  case amount -> "金额";
                  case currency -> "币种";
                  case region -> "地区";
                  case createdAt -> "下单时间";
                })
        .toList();
  }

  public List<String> cells(Order order, List<ExportField> fields) {
    return fields.stream()
        .map(
            field ->
                text(
                    switch (field) {
                      case orderNo -> order.orderNo();
                      case customerName -> order.customerName();
                      case customerPhone -> order.customerPhone();
                      case status -> STATUS.getOrDefault(order.status(), "未知");
                      case channel -> CHANNEL.getOrDefault(order.channel(), "未知");
                      case amount ->
                          order
                              .amount()
                              .setScale(2, java.math.RoundingMode.UNNECESSARY)
                              .toPlainString();
                      case currency -> order.currency();
                      case region -> order.region();
                      case createdAt -> TIME.format(order.createdAt());
                    }))
        .toList();
  }

  public String fileName(TaskSnapshot snapshot) {
    return snapshot.fileName();
  }

  private String text(String value) {
    if (value == null || value.length() > 32767)
      throw new InvalidExportDataException("EXPORT_CELL_INVALID");
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch < 32 && ch != '\t' && ch != '\r' && ch != '\n' || ch == 0xFFFE || ch == 0xFFFF)
        throw new InvalidExportDataException("EXPORT_CELL_INVALID");
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))
          throw new InvalidExportDataException("EXPORT_CELL_INVALID");
      } else if (Character.isLowSurrogate(ch))
        throw new InvalidExportDataException("EXPORT_CELL_INVALID");
    }
    return value;
  }
}
