// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.orders.api;

import static com.exportflow.orders.api.OrderApiModels.*;

import com.exportflow.orders.application.OrderQueryService;
import com.exportflow.shared.domain.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** HTTP只做结构校验及映射；设计 contracts/http-api.md，验证 TC-214/215。 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
  private final OrderQueryService service;
  private final OrderApiMapper mapper;

  public OrderController(OrderQueryService service, OrderApiMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public ResponseEntity<PageResult<OrderResponse>> getOrders(
      @RequestParam MultiValueMap<String, String> params) {
    if (params.values().stream().anyMatch(values -> values.size() != 1))
      throw BusinessException.validation();
    var page = service.query(mapper.toQuery(new OrderQueryDto(params.toSingleValueMap())));
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(
            new PageResult<>(
                page.items().stream().map(mapper::toResponse).toList(),
                page.page(),
                page.pageSize(),
                page.total()));
  }
}
