// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.exporttasks.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 创建传输模型；设计 contracts/http-api.md，不包含执行状态或详情地址。 */
public final class ExportTaskApiModels {
  private ExportTaskApiModels() {}

  public record CreateTaskRequestDto(JsonNode scope, List<String> fields) {}

  public record CreateTaskResponse(
      String taskId,
      String taskNo,
      String scopeType,
      long totalCount,
      List<String> fields,
      String createdAt) {
    public CreateTaskResponse {
      fields = List.copyOf(fields);
    }
  }
}
