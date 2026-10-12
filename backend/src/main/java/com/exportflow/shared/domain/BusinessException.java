// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.shared.domain;

import java.util.Map;

/** 可安全映射的业务结论；设计 function-design-reference.md，不保留底层异常原文。 */
public class BusinessException extends RuntimeException {
  private final String code;
  private final int status;
  private final String outcome;
  private final Map<String, Long> details;

  public BusinessException(String code, String message, int status) {
    this(code, message, status, "REJECTED", null);
  }

  public BusinessException(
      String code, String message, int status, String outcome, Map<String, Long> details) {
    super(message);
    this.code = code;
    this.status = status;
    this.outcome = outcome;
    this.details = details == null ? null : Map.copyOf(details);
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }

  public String outcome() {
    return outcome;
  }

  public Map<String, Long> details() {
    return details;
  }

  public static BusinessException validation() {
    return new BusinessException("VALIDATION_ERROR", "输入有误，请检查筛选或导出配置", 400);
  }
}
