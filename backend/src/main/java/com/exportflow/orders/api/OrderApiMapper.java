// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.orders.api;

import static com.exportflow.orders.api.OrderApiModels.*;

import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import com.exportflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 严格白名单和秒级UTC解析；设计 contracts/http-api.md，验证 TC-214/215。 */
@Component
public class OrderApiMapper {
  private static final Set<String> FILTERS =
      Set.of(
          "keyword",
          "orderNo",
          "status",
          "channel",
          "createdFrom",
          "createdTo",
          "minAmount",
          "maxAmount");

  public OrderQuery toQuery(OrderQueryDto dto) {
    var values = dto.values();
    if (values.keySet().stream()
        .anyMatch(
            key ->
                !FILTERS.contains(key)
                    && !Set.of("page", "pageSize", "sortDirection").contains(key)))
      throw BusinessException.validation();
    var filterValues = new java.util.HashMap<>(values);
    filterValues.keySet().removeAll(Set.of("page", "pageSize", "sortDirection"));
    var query =
        new OrderQuery(
            toFilter(filterValues),
            integer(values.getOrDefault("page", "1")),
            integer(values.getOrDefault("pageSize", "20")),
            values.getOrDefault("sortDirection", "DESC"));
    query.validate();
    return query;
  }

  public OrderFilter toFilter(Map<String, String> values) {
    if (!FILTERS.containsAll(values.keySet())) throw BusinessException.validation();
    var result =
        new OrderFilter(
            text(values.get("keyword")),
            text(values.get("orderNo")),
            text(values.get("status")),
            text(values.get("channel")),
            instant(values.get("createdFrom")),
            instant(values.get("createdTo")),
            money(values.get("minAmount")),
            money(values.get("maxAmount")));
    result.validate();
    return result;
  }

  private String text(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private int integer(String value) {
    if (!value.matches("[1-9][0-9]{0,6}")) throw BusinessException.validation();
    return Integer.parseInt(value);
  }

  private Instant instant(String value) {
    if (value == null) return null;
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z"))
      throw BusinessException.validation();
    try {
      Instant result = Instant.parse(value);
      if (!result.toString().equals(value)) throw BusinessException.validation();
      return result;
    } catch (java.time.DateTimeException ex) {
      throw BusinessException.validation();
    }
  }

  private BigDecimal money(String value) {
    if (value == null) return null;
    if (!value.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")) throw BusinessException.validation();
    return new BigDecimal(value).setScale(2);
  }

  public OrderResponse toResponse(Order order) {
    return new OrderResponse(
        Long.toString(order.id()),
        order.orderNo(),
        order.customerName(),
        order.customerPhone(),
        order.status(),
        order.channel(),
        order.amount().setScale(2).toPlainString(),
        order.currency(),
        order.region(),
        order.createdAt().toString());
  }
}
