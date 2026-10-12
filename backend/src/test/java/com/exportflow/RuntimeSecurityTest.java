// 规格与进度：[任务记录](../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.BoundaryFixture;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;

// [AC-061](../../../../../../docs/product/acceptance-criteria.md#ac-061)
// [AC-065](../../../../../../docs/product/acceptance-criteria.md#ac-065)
// [AC-018](../../../../../../docs/product/acceptance-criteria.md#ac-018)
@ExtendWith(OutputCaptureExtension.class)
class RuntimeSecurityTest extends BoundaryFixture {
  private boolean restricted;

  @Override
  protected String databaseUsername() {
    return restricted ? mysql.getRestrictedUsername() : super.databaseUsername();
  }

  @Override
  protected String databasePassword() {
    return restricted ? "synthetic-runtime-only" : super.databasePassword();
  }

  @Override
  protected List<String> applicationArguments() {
    return restricted
        ? List.of(
            "--spring.flyway.user=" + mysql.getUsername(),
            "--spring.flyway.password=" + mysql.getPassword())
        : List.of();
  }

  @Test
  void TC213_runtimeRoleCannotWriteOrdersOrDdlAndMigratorIsSeparate() throws Exception {
    mysql.createRestrictedUser("synthetic-runtime-only");
    try (var rootConnection = mysql.getAdminConnection();
        var sql = rootConnection.createStatement()) {
      sql.execute(
          "GRANT SELECT ON `"
              + mysql.getDatabaseName()
              + "`.* TO '"
              + mysql.getRestrictedUsername()
              + "'@'%'");
      for (String table :
          List.of(
              "export_tasks",
              "export_task_orders",
              "export_idempotency",
              "command_outbox",
              "dispatch_attempts",
              "task_execution",
              "export_checkpoints",
              "export_data_blocks",
              "export_artifacts",
              "export_cleanup_candidates"))
        sql.execute(
            "GRANT INSERT,UPDATE,DELETE ON `"
                + mysql.getDatabaseName()
                + "`."
                + table
                + " TO '"
                + mysql.getRestrictedUsername()
                + "'@'%'");
    }
    restricted = true;
    restartApplication();
    try (var connection = app.getBean(javax.sql.DataSource.class).getConnection();
        var sql = connection.createStatement()) {
      for (String command :
          List.of(
              "UPDATE orders SET customer_name=customer_name WHERE id=1",
              "DELETE FROM orders WHERE id=-1",
              "CREATE TABLE synthetic_forbidden(id INT)",
              "TRUNCATE TABLE orders"))
        assertThatThrownBy(() -> sql.execute(command)).isInstanceOf(java.sql.SQLException.class);
    }
    assertThat(get("/api/v1/orders").statusCode()).isEqualTo(200);
    assertThat(post(filtered(), newKey()).statusCode()).isEqualTo(201);
  }

  @Test
  void TC213_databaseRejectsInvalidCountsAndCascadingRemovalOfPermanentReferences()
      throws Exception {
    var task = create();
    assertThatThrownBy(
            () ->
                db.update(
                    "UPDATE export_tasks SET processed_count=total_count+1 WHERE task_id=?",
                    task.toString()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                db.update(
                    "UPDATE export_tasks SET total_count=200001 WHERE task_id=?", task.toString()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> db.update("DELETE FROM export_tasks WHERE task_id=?", task.toString()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(count("export_tasks")).isEqualTo(1);
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(count("export_idempotency")).isEqualTo(1);
  }

  @Test
  void TC214_queryAndCreationNeverLogSyntheticCustomerKeyOrPhysicalPath(CapturedOutput output)
      throws Exception {
    String privateValue = "synthetic-private-phone-0013800000001",
        key = "synthetic-private-key-1234567890";
    get("/api/v1/orders?keyword=" + privateValue);
    post(
        filtered().replace("\"filter\":{}", "\"filter\":{\"keyword\":\"" + privateValue + "\"}"),
        key);
    assertThat(output.toString()).doesNotContain(privateValue, key, "=测试客户", root.toString());
  }

  @Test
  void TC213_disabledConsumerHasRegisteredStoppedContainersAndBoundedManualAck() throws Exception {
    var registry =
        app.getBean(org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class);
    assertThat(registry.getListenerContainers()).isNotEmpty();
    for (var container : registry.getListenerContainers()) {
      assertThat(container.isRunning()).isFalse();
      var listener =
          (org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer) container;
      assertThat(listener.getAcknowledgeMode())
          .isEqualTo(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
      assertThat(
              org.springframework.test.util.ReflectionTestUtils.getField(
                  listener, "concurrentConsumers"))
          .isEqualTo(1);
      assertThat(
              org.springframework.test.util.ReflectionTestUtils.getField(listener, "prefetchCount"))
          .isEqualTo(1);
    }
  }

  @Test
  void TC223_generationDiagnosticsNeverLogPrivateValuesPayloadKeyOrPhysicalRoot(
      CapturedOutput output) throws Exception {
    String key = "synthetic-private-export-12345678";
    String payload =
        "{\"scope\":{\"type\":\"SELECTED\",\"orderIds\":[\"1\"]},\"fields\":[\"customerName\"]}";
    var response = post(payload, key);
    assertThat(response.statusCode()).isEqualTo(201);
    probe.on(
        "com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler",
        "assemble",
        (m, a, real) -> {
          throw new com.exportflow.exporttasks.domain.ExportFileModels.InvalidExportDataException(
              "EXPORT_CELL_INVALID");
        });
    var task = UUID.fromString(body(response).get("taskId").asText());
    var lease = lease(task);
    var result = generate(task, lease);
    assertThat(com.exportflow.testing.BoundaryProbe.property(result, "kind").toString())
        .isEqualTo("KNOWN_FAILURE");
    assertThat(output.toString()).doesNotContain(key, payload, root.toString());
    var source = db.queryForMap("SELECT customer_name,customer_phone FROM orders WHERE id=1");
    for (var column : java.util.List.of("customer_name", "customer_phone")) {
      String original = source.get(column).toString();
      if (!original.isBlank())
        assertThat(output.toString().contains(original)).as("生成诊断不泄露来源客户字段（不输出原值）").isFalse();
    }
  }
}
