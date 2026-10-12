// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.shared.api;

import com.exportflow.shared.domain.BusinessException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 不向客户端或日志复制异常原文；规格 contracts/http-api.md，验证 TC-214。 */
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex) {
    return handleBusiness(new BusinessException("NOT_FOUND", "请求资源不存在", 404));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(Exception ex) {
    return handleBusiness(new BusinessException("METHOD_NOT_ALLOWED", "请求方法不支持", 405));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex) {
    return ResponseEntity.status(ex.status())
        .header("Cache-Control", "no-store")
        .body(
            new ApiErrorResponse(
                ex.code(),
                ex.getMessage(),
                UUID.randomUUID().toString(),
                ex.outcome(),
                ex.details()));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiErrorResponse> handleValidation(Exception ex) {
    return handleBusiness(BusinessException.validation());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
    return handleBusiness(
        new BusinessException("INTERNAL_ERROR", "服务暂时异常，请稍后重试", 500, "UNKNOWN", null));
  }
}
