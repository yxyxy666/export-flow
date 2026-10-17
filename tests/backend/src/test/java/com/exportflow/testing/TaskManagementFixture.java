// 仅测试夹具：[计划](../../../../../../../../specs/004-task-management/plan.md)、[进度](../../../../../../../../specs/004-task-management/tasks.md)。
package com.exportflow.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.exportflow.exporttasks.domain.ExportFileModels.CompletedDataset;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.http.*;
import java.util.*;

/** 复用真实生产入口与隔离服务；不提供业务端点、迁移或修复替代实现。 */
public abstract class TaskManagementFixture extends BoundaryFixture {
  protected static final String REPAIR = "com.exportflow.worker.port.AssemblyRepairRepository";
  protected static final String REPAIR_SERVICE = "com.exportflow.worker.application.AssemblyRepairService";

  /** 按实参类型选择公开端口，避免新旧commitBlock同参数个数重载被误选。 */
  @Override
  protected Object port(String portClass, String name, Object... args) throws Exception {
    var type = Class.forName(portClass);
    var matches = Arrays.stream(type.getMethods())
        .filter(m -> m.getName().equals(name) && accepts(m, args)).toList();
    if (matches.size() != 1) throw new NoSuchMethodException(portClass + "." + name + "：兼容签名数量=" + matches.size());
    return matches.getFirst().invoke(app.getBean(type), args);
  }

  private boolean accepts(Method method, Object[] args) {
    if (method.getParameterCount() != args.length) return false;
    Map<Class<?>, Class<?>> boxing = Map.of(long.class, Long.class, int.class, Integer.class, boolean.class, Boolean.class);
    var types = method.getParameterTypes();
    for (int i = 0; i < args.length; i++) {
      if (args[i] == null) { if (types[i].isPrimitive()) return false; }
      else if (!boxing.getOrDefault(types[i], types[i]).isInstance(args[i])) return false;
    }
    return true;
  }

  @Override
  protected Object guard(boolean valid) throws Exception {
    var type = com.exportflow.testing.BoundaryProbe.method(GENERATOR, "generate").getParameterTypes()[1];
    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
      if (method.getName().equals("check")) return valid;
      if (method.getName().equals("stopReason")) return com.exportflow.testing.BoundaryProbe.enumValue(
          method.getReturnType(), valid ? "NONE" : "LOST_AUTHORITY");
      if (method.getName().equals("toString")) return "TestExecutionGuard";
      throw new UnsupportedOperationException(method.getName());
    });
  }

  protected HttpResponse<String> pause(UUID task, UUID operation, String version) throws Exception {
    return pauseBody(task, json.writeValueAsString(Map.of("operationId", operation.toString(),
        "expectedControlVersion", version)));
  }

  protected HttpResponse<String> pauseBody(UUID task, String payload) throws Exception {
    return http.send(HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/" + task + "/pause"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
  }

  protected JsonNode listed() throws Exception {
    var response = get("/api/v1/export-tasks?page=1&pageSize=20");
    assertThat(response.statusCode()).as("任务查询必须已实现，不能把405当数据库故障").isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    return body(response);
  }

  protected JsonNode listedTask(UUID task) throws Exception {
    for (var item : listed().path("items"))
      if (item.path("taskId").asText().equals(task.toString())) return item;
    throw new AssertionError("权威列表缺少测试任务");
  }

  protected CompletedDataset collect(UUID task, Object lease) throws Exception {
    return (CompletedDataset) call("com.exportflow.worker.application.ExportDataCollectionService",
        "collect", context(task, lease), guard(true));
  }

  /** 缩小实际只读批次，使六条既有订单形成三块；不造数、不替代来源查询。 */
  protected void useThreeSmallBlocks() throws Exception {
    useOrderVolume(6);
    probe.on(ORDERS, "readBatch", (method, args, real) -> { args[3] = 2; return real.run(); });
  }
}
