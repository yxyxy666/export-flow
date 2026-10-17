// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 加载真正生产入口；入口缺失直接失败，绝不以跳过或测试替代后端掩盖回归。 */
public abstract class ProductionBackendFixture {
  protected final ObjectMapper json = new ObjectMapper();
  protected HttpClient http;
  protected JdbcTemplate db;
  protected ConfigurableApplicationContext app;
  protected SharedServices.Mysql mysql;
  protected SharedServices.Rabbit rabbit;
  protected Path root;
  protected URI base;

  protected boolean consumerEnabled() {
    return false;
  }

  protected boolean schedulingEnabled() {
    return false;
  }

  protected int serverPort() {
    return 0;
  }

  protected String databaseUsername() {
    return mysql.getUsername();
  }

  protected String databasePassword() {
    return mysql.getPassword();
  }

  protected java.util.List<String> applicationArguments() {
    return java.util.List.of();
  }

  protected org.springframework.beans.factory.config.BeanPostProcessor testFaults() {
    return null;
  }

  @BeforeEach
  void startProduction() throws Exception {
    Class<?> entry = Class.forName("com.exportflow.ExportFlowApplication");
    http = HttpClient.newHttpClient();
    mysql = SharedServices.mysql();
    mysql.start();
    rabbit = SharedServices.rabbit();
    rabbit.start();
    root = Files.createTempDirectory("export-flow-test-");
    var source =
        new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    db = new JdbcTemplate(source);
    startApplication(entry);
    selectProvidedOrders();
  }

  protected void restartApplication() throws Exception {
    app.close();
    startApplication(Class.forName("com.exportflow.ExportFlowApplication"));
  }

  protected void startApplication(Class<?> entry) throws Exception {
    var builder = new SpringApplicationBuilder(entry);
    // 测试手动调用获批调度入口，避免后台时钟与竞态断言争用同一记录。
    if (!schedulingEnabled())
      builder.initializers(
          context ->
              context.addBeanFactoryPostProcessor(
                  factory -> {
                    var registry =
                        (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
                    for (String name :
                        java.util.List.of(
                            "org.springframework.context.annotation.internalScheduledAnnotationProcessor",
                            "dispatchScheduler",
                            "runtimeMonitor"))
                      if (registry.containsBeanDefinition(name))
                        registry.removeBeanDefinition(name);
                  }));
    var faults = testFaults();
    if (faults != null)
      builder.initializers(context -> context.getBeanFactory().addBeanPostProcessor(faults));
    var arguments =
        new java.util.ArrayList<String>(
            java.util.List.of(
                "--server.address=127.0.0.1",
                "--server.port=" + serverPort(),
                "--spring.datasource.url=" + mysql.getJdbcUrl(),
                "--spring.datasource.username=" + databaseUsername(),
                "--spring.datasource.password=" + databasePassword(),
                "--spring.rabbitmq.addresses=" + rabbit.getAmqpUrl(),
                "--spring.rabbitmq.virtual-host=" + rabbit.getVirtualHost(),
                "--export-flow.management-url=http://"
                    + rabbit.getHost()
                    + ":"
                    + rabbit.getMappedPort(15672),
                "--export-flow.file-root=" + root,
                "--logging.file.name=" + root.resolve("application.log"),
                "--logging.logback.rollingpolicy.file-name-pattern="
                    + root.resolve("application.log.%d{yyyy-MM-dd}.%i.gz"),
                "--export-flow.consumer-enabled=" + consumerEnabled()));
    arguments.addAll(applicationArguments());
    app = builder.run(arguments.toArray(String[]::new));
    base =
        URI.create(
            "http://127.0.0.1:"
                + ((ServletWebServerApplicationContext) app).getWebServer().getPort());
  }

  @AfterEach
  void closeProduction() throws Exception {
    // 每个用例拥有独立schema/队列/目录，故障和未ACK消息不污染下个用例。
    try {
      if (app != null) app.close();
    } finally {
      try {
        if (http != null) http.close();
      } finally {
        try {
          if (rabbit != null) rabbit.stop();
        } finally {
          try {
            if (mysql != null) mysql.stop();
          } finally {
            org.mockito.Mockito.framework().clearInlineMocks();
          }
        }
      }
    }
    // 不递归删目录；失败/恢复产物保留供诊断，目录始终由测试新建。
  }

  protected void selectProvidedOrders() throws Exception {
    useOrderVolume(4);
  }

  protected HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(base.resolve(path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected HttpResponse<String> post(String body, String key) throws Exception {
    var request =
        HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks"))
            .header("Content-Type", "application/json");
    if (key != null) request.header("Idempotency-Key", key);
    return http.send(
        request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected JsonNode body(HttpResponse<String> response) throws Exception {
    return json.readTree(response.body());
  }

  protected String newKey() {
    return UUID.randomUUID().toString();
  }

  protected String filtered() {
    return "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\",\"amount\",\"createdAt\"]}";
  }

  protected UUID create() throws Exception {
    var response = post(filtered(), newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    return UUID.fromString(body(response).get("taskId").asText());
  }

  protected Object call(String beanClass, String method, Object... args) throws Exception {
    Class<?> requested = Class.forName(beanClass);
    Object bean;
    try {
      bean = app.getBean(requested);
    } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException
        | org.springframework.beans.factory.BeanNotOfRequiredTypeException proxied) {
      // 故障探针用真实端口代理生产适配器，按其公开端口取得同一实例。
      var port =
          Arrays.stream(requested.getInterfaces())
              .filter(type -> type.getName().contains(".port."))
              .findFirst()
              .orElseThrow(() -> proxied);
      bean = app.getBean(port);
    }
    var target =
        Arrays.stream(bean.getClass().getMethods())
            .filter(m -> m.getName().equals(method) && m.getParameterCount() == args.length)
            .findFirst()
            .orElseThrow();
    return target.invoke(bean, args);
  }

  protected Object port(String portClass, String method, Object... args) throws Exception {
    var type = Class.forName(portClass);
    var boxing = java.util.Map.<Class<?>,Class<?>>of(long.class, Long.class, int.class, Integer.class,
        boolean.class, Boolean.class);
    var matches = Arrays.stream(type.getMethods()).filter(candidate -> {
      if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) return false;
      var parameters = candidate.getParameterTypes();
      for (int i=0;i<args.length;i++) {
        if (args[i] == null) { if (parameters[i].isPrimitive()) return false; }
        else if (!boxing.getOrDefault(parameters[i],parameters[i]).isInstance(args[i])) return false;
      }
      return true;
    }).toList();
    if (matches.size()!=1) throw new NoSuchMethodException(portClass+"."+method+"：兼容签名数量="+matches.size());
    return matches.getFirst().invoke(app.getBean(type), args);
  }

  protected com.rabbitmq.client.Connection brokerConnection() throws Exception {
    var factory = new com.rabbitmq.client.ConnectionFactory();
    factory.setUri(rabbit.getAmqpUrl());
    factory.setVirtualHost(rabbit.getVirtualHost());
    return factory.newConnection();
  }

  protected byte[] command(UUID task) throws Exception {
    String event =
        db.queryForObject(
            "SELECT event_id FROM command_outbox WHERE task_id=?", String.class, task.toString());
    return json.writeValueAsBytes(
        Map.of(
            "eventId",
            event,
            "taskId",
            task.toString(),
            "schemaVersion",
            1,
            "type",
            "EXPORT_REQUESTED",
            "traceId",
            "synthetic-worker-test"));
  }

  protected Map<String, Long> creationCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String table :
        List.of("export_tasks", "command_outbox", "export_idempotency", "export_task_orders"))
      counts.put(table, count(table));
    return counts;
  }

  protected String state(UUID task) {
    return db.queryForObject(
        "SELECT status FROM export_tasks WHERE task_id=?", String.class, task.toString());
  }

  protected long count(String table) {
    if (!Set.of(
            "orders",
            "export_tasks",
            "command_outbox",
            "export_idempotency",
            "export_task_orders",
            "dispatch_attempts",
            "export_artifacts")
        .contains(table)) throw new IllegalArgumentException("测试表白名单");
    return Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
  }

  protected void awaitSuccess(UUID task) {
    try {
      call("com.exportflow.dispatch.application.OutboxDispatchService", "scanAvailable");
    } catch (Exception failure) {
      throw new AssertionError("真实命令投递失败", failure);
    }
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofMinutes(5))
        .untilAsserted(() -> assertThat(state(task)).isEqualTo("SUCCEEDED"));
  }

  protected byte[] download(UUID task) throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/" + task + "/file"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(response.headers().firstValueAsLong("Content-Length").orElseThrow())
        .isEqualTo(response.body().length);
    String disposition = response.headers().firstValue("Content-Disposition").orElseThrow();
    assertThat(disposition).contains("filename*=UTF-8''").doesNotContain("\r", "\n");
    var fallback =
        java.util.regex.Pattern.compile("(?:^|;)\\s*filename=\"([^\"]+)\"").matcher(disposition);
    assertThat(fallback.find()).as("必须有安全ASCII fallback").isTrue();
    assertThat(fallback.group(1)).matches("[A-Za-z0-9_.-]+\\.xlsx");
    var utf8 = java.util.regex.Pattern.compile("filename\\*=UTF-8''([^;]+)").matcher(disposition);
    assertThat(utf8.find()).isTrue();
    String actualName =
        java.net.URLDecoder.decode(utf8.group(1), java.nio.charset.StandardCharsets.UTF_8);
    var metadata =
        db.queryForMap(
            "SELECT task_no,created_at FROM export_tasks WHERE task_id=?", task.toString());
    // Connector/J 9 对DATETIME默认返回LocalDateTime；显式类型读取保持UTC断言。
    var created =
        db.queryForObject(
                "SELECT created_at FROM export_tasks WHERE task_id=?",
                java.time.LocalDateTime.class,
                task.toString())
            .toInstant(java.time.ZoneOffset.UTC);
    String expectedName =
        "订单导出_"
            + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneId.of("Asia/Hong_Kong"))
                .format(created)
            + "_"
            + metadata.get("task_no")
            + ".xlsx";
    assertThat(actualName).isEqualTo(expectedName);
    return response.body();
  }

  protected void useOrderVolume(int count) throws Exception {
    if (count < 1) throw new IllegalArgumentException("数据范围无效");
    long available = db.queryForObject("SELECT COUNT(*) FROM exportflow.orders", Long.class);
    Assumptions.assumeTrue(available >= count,
        "数据前置条件缺口：exportflow.orders 实有 " + available + " 条，本用例需要 " + count + " 条；按负责人要求只记录，不计业务失败、不造数，见provided-data.md");
    long last = db.queryForObject("SELECT id FROM exportflow.orders ORDER BY id LIMIT 1 OFFSET ?", Long.class, count - 1);
    // 只改本用例的视图范围；来源仍是原表，没有物化复制或数据DML。
    db.execute("CREATE OR REPLACE ALGORITHM=MERGE SQL SECURITY INVOKER VIEW orders AS "
        + "SELECT id,order_no,customer_name,COALESCE(customer_phone,'') customer_phone,"
        + "COALESCE(order_status,status) status,sales_channel channel,total_amount amount,"
        + "currency,COALESCE(shipping_province,'') region,CAST(created_at AS DATETIME) created_at "
        + "FROM exportflow.orders WHERE id<=" + last);
  }

  protected UUID createVolume() throws Exception {
    var response =
        post(
            "{\"scope\":{\"type\":\"FILTERED\",\"filter\":{}},\"fields\":[\"orderNo\",\"customerName\",\"customerPhone\",\"status\",\"channel\",\"amount\",\"currency\",\"region\",\"createdAt\"]}",
            newKey());
    assertThat(response.statusCode()).isEqualTo(201);
    return UUID.fromString(body(response).get("taskId").asText());
  }

  protected Process startWorker(boolean barrier) throws Exception {
    return startWorker(
        barrier
            ? java.util.List.of(
                "--test-barrier-port=com.exportflow.worker.port.CheckpointRepository",
                "--test-barrier-method=commitBlock",
                "--test-barrier-phase=after",
                "--test-barrier-marker=block-committed.marker")
            : java.util.List.of());
  }

  protected Process startWorker(java.util.List<String> barriers) throws Exception {
    boolean barrier = !barriers.isEmpty();
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
    command.addAll(
        List.of(
            "-Xms512m",
            "-Xmx512m",
            "-Duser.timezone=UTC",
            "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            barriers.stream().anyMatch(a -> a.startsWith("--test-heap-report="))
                ? "com.exportflow.testing.WorkerSampledMain"
                : barrier
                    ? "com.exportflow.testing.WorkerFaultBarrierMain"
                    : "com.exportflow.ExportFlowApplication",
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.datasource.url=" + mysql.getJdbcUrl(),
            "--spring.datasource.username=" + mysql.getUsername(),
            "--spring.datasource.password=" + mysql.getPassword(),
            "--spring.rabbitmq.addresses=" + rabbit.getAmqpUrl(),
            "--spring.rabbitmq.virtual-host=" + rabbit.getVirtualHost(),
            "--export-flow.management-url=http://"
                + rabbit.getHost()
                + ":"
                + rabbit.getMappedPort(15672),
            "--export-flow.file-root=" + root,
            "--logging.file.name=" + root.resolve("worker.log"),
            "--logging.logback.rollingpolicy.file-name-pattern="
                + root.resolve("worker.log.%d{yyyy-MM-dd}.%i.gz"),
            "--export-flow.consumer-enabled=true"));
    String provider = System.getProperty("java.nio.channels.spi.SelectorProvider");
    if (provider != null) command.add(1, "-Djava.nio.channels.spi.SelectorProvider=" + provider);
    String socketTemp = System.getProperty("jdk.net.unixdomain.tmpdir");
    if (socketTemp != null) command.add(1, "-Djdk.net.unixdomain.tmpdir=" + socketTemp);
    if (barrier) {
      command.add("--test-barrier-root=" + root);
      command.addAll(barriers);
    }
    return new ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(
            root.resolve(barrier ? "worker-barrier.log" : "worker-restart.log").toFile())
        .start();
  }

  protected void stopWorker(Process worker) throws Exception {
    worker.destroy();
    if (!worker.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
      worker.destroyForcibly();
      assertThat(worker.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
  }
}
