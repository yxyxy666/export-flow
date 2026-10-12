// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
package com.exportflow.exporttasks.application;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.*;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

/** 版本1语义规范化；设计 data-model.md，验证 TC-217。 */
@Component
public class ExportRequestCanonicalizer {
  private final ObjectMapper json;

  public ExportRequestCanonicalizer(ObjectMapper json) {
    this.json = json;
  }

  public CanonicalSubmission normalize(CreateExportCommand command) {
    command.scope().validate();
    ExportField.validateFields(command.fields());
    ExportScope scope = command.scope();
    if (scope.orderIds() != null)
      scope =
          new ExportScope("SELECTED", scope.orderIds().stream().distinct().sorted().toList(), null);
    else {
      var f = scope.filter();
      scope =
          new ExportScope(
              "FILTERED",
              null,
              new OrderFilter(
                  f.keyword() == null ? null : f.keyword().toLowerCase(Locale.ROOT),
                  f.orderNo(),
                  f.status(),
                  f.channel(),
                  f.createdFrom(),
                  f.createdTo(),
                  f.minAmount(),
                  f.maxAmount()),
              scope.excludedOrderIds().stream().distinct().sorted().toList());
    }
    var payload = new LinkedHashMap<String, Object>();
    // 显式保持旧scope三字段及null顺序，避免record新增字段改变历史摘要。
    var canonicalScope = new LinkedHashMap<String, Object>();
    canonicalScope.put("type", scope.type());
    canonicalScope.put("orderIds", scope.orderIds());
    canonicalScope.put("filter", scope.filter());
    if (!scope.excludedOrderIds().isEmpty())
      canonicalScope.put("excludedOrderIds", scope.excludedOrderIds());
    payload.put("scope", canonicalScope);
    payload.put("fields", command.fields());
    try {
      return new CanonicalSubmission(
          scope.excludedOrderIds().isEmpty() ? 1 : 2,
          hash(json.writeValueAsBytes(payload)),
          hash(command.key().getBytes(StandardCharsets.UTF_8)),
          scope,
          command.fields());
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("意图编码失败");
    }
  }

  private byte[] hash(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("摘要能力不可用");
    }
  }
}
