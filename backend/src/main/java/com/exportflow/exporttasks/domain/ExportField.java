// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)。
package com.exportflow.exporttasks.domain;

import com.exportflow.shared.domain.BusinessException;
import java.util.HashSet;
import java.util.List;

/** 固定字段协议，保持调用者顺序；设计 data-model.md，验证 TC-216/233。 */
public enum ExportField {
  orderNo,
  customerName,
  customerPhone,
  status,
  channel,
  amount,
  currency,
  region,
  createdAt;

  public static void validateFields(List<ExportField> fields) {
    if (fields == null
        || fields.isEmpty()
        || fields.size() > 9
        || fields.stream().anyMatch(java.util.Objects::isNull)
        || new HashSet<>(fields).size() != fields.size())
      throw new BusinessException("INVALID_EXPORT_FIELDS", "请至少选择一个有效字段", 400);
  }
}
