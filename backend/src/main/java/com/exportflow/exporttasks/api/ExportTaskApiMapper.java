// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)。
package com.exportflow.exporttasks.api;

import static com.exportflow.exporttasks.api.ExportTaskApiModels.*;
import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.*;
import com.exportflow.orders.api.OrderApiMapper;
import com.exportflow.shared.domain.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

/** 结构白名单、字符串Long与互斥范围；设计 contracts/http-api.md，验证 TC-216。 */
@Component
public class ExportTaskApiMapper {
  private final OrderApiMapper orders;

  public ExportTaskApiMapper(OrderApiMapper orders) {
    this.orders = orders;
  }

  public CreateExportCommand toCommand(CreateTaskRequestDto dto, String key) {
    if (key == null
        || !key.matches("[A-Za-z0-9_-]{16,128}")
        || dto == null
        || dto.scope() == null
        || !dto.scope().isObject()) throw BusinessException.validation();
    JsonNode node = dto.scope();
    String type = node.path("type").asText();
    ExportScope scope;
    if ("SELECTED".equals(type)) {
      checkFields(node, Set.of("type", "orderIds"), Set.of());
      scope = new ExportScope(type, parseOrderIds(node.get("orderIds"), false), null);
    } else if ("FILTERED".equals(type)) {
      checkFields(node, Set.of("type", "filter"), Set.of("excludedOrderIds"));
      var filter = node.get("filter");
      if (filter == null || !filter.isObject()) throw BusinessException.validation();
      Map<String, String> values = new LinkedHashMap<>();
      filter
          .properties()
          .forEach(
              entry -> {
                if (!entry.getValue().isTextual()) throw BusinessException.validation();
                values.put(entry.getKey(), entry.getValue().asText());
              });
      scope =
          new ExportScope(
              type,
              null,
              orders.toFilter(values),
              node.has("excludedOrderIds")
                  ? parseOrderIds(node.get("excludedOrderIds"), true)
                  : List.of());
    } else throw BusinessException.validation();
    scope.validate();
    if (dto.fields() == null)
      throw new BusinessException("INVALID_EXPORT_FIELDS", "请至少选择一个有效字段", 400);
    List<ExportField> fields = new ArrayList<>();
    for (var value : dto.fields()) {
      try {
        fields.add(ExportField.valueOf(value));
      } catch (IllegalArgumentException | NullPointerException ex) {
        throw new BusinessException("INVALID_EXPORT_FIELDS", "请至少选择一个有效字段", 400);
      }
    }
    ExportField.validateFields(fields);
    return new CreateExportCommand(scope, fields, key);
  }

  /** 在去重前校验排除原输入长度，避免重复项绕过容量边界。 */
  private List<Long> parseOrderIds(JsonNode node, boolean allowEmpty) {
    if (node == null || !node.isArray() || (allowEmpty && node.size() > 200000))
      throw BusinessException.validation();
    if (!allowEmpty && node.isEmpty())
      throw new BusinessException("EMPTY_EXPORT_SCOPE", "导出范围为空", 400);
    List<Long> ids = new ArrayList<>();
    for (var id : node) {
      if (!id.isTextual() || !id.asText().matches("[1-9][0-9]{0,18}"))
        throw BusinessException.validation();
      try {
        ids.add(Long.parseLong(id.asText()));
      } catch (NumberFormatException ex) {
        throw BusinessException.validation();
      }
    }
    return ids.stream().distinct().sorted().toList();
  }

  private void checkFields(JsonNode node, Set<String> required, Set<String> optional) {
    if (required.stream().anyMatch(name -> !node.has(name))
        || node.properties().stream()
            .anyMatch(
                entry -> !required.contains(entry.getKey()) && !optional.contains(entry.getKey())))
      throw BusinessException.validation();
  }

  public CreateTaskResponse toResponse(TaskSummary summary) {
    return new CreateTaskResponse(
        summary.taskId().toString(),
        summary.taskNo(),
        summary.scopeType(),
        summary.totalCount(),
        summary.fields().stream().map(Enum::name).toList(),
        summary.createdAt().toString());
  }
}
