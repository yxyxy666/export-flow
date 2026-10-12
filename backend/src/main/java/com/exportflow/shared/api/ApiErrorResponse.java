// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 安全错误协议；规格 contracts/http-api.md，验证 TC-214/216。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    String code, String message, String traceId, String outcome, Map<String, Long> details) {}
