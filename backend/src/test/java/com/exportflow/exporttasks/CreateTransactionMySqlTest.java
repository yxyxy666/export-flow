// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-050](../../../../../../../docs/product/acceptance-criteria.md#ac-050)
// [AC-051](../../../../../../../docs/product/acceptance-criteria.md#ac-051)
class CreateTransactionMySqlTest extends ProductionBackendFixture {
  private final com.exportflow.testing.CommitFaults faults =
      new com.exportflow.testing.CommitFaults();

  @Override
  protected org.springframework.beans.factory.config.BeanPostProcessor testFaults() {
    return faults;
  }

  @Test
  void TC218_lostCommitAcknowledgementIsReconciledOnNewConnectionToSameTask() throws Exception {
    String key = newKey();
    long before = count("export_tasks");
    faults.arm(false);
    try {
      var response = post(filtered(), key);
      assertThat(response.statusCode()).isIn(200, 201);
      assertThat(faults.triggeredCount()).as("必须确实触发真实提交后的异常").isEqualTo(1);
      assertThat(count("export_tasks")).isEqualTo(before + 1);
      var replay = post(filtered(), key);
      assertThat(replay.statusCode()).isEqualTo(200);
      assertThat(body(replay).get("taskId")).isEqualTo(body(response).get("taskId"));
    } finally {
      faults.restore();
    }
  }

  @Test
  void TC218_unreadableReconciliationReturnsUnknownThenOriginalKeyFindsCommittedTask()
      throws Exception {
    String key = newKey();
    long before = count("export_tasks");
    faults.arm(true);
    try {
      var response = post(filtered(), key);
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(faults.triggeredCount()).as("不能用普通成功或其他503替代提交故障证据").isEqualTo(1);
      assertThat(body(response).get("outcome").asText()).isEqualTo("UNKNOWN");
      assertThat(count("export_tasks")).isEqualTo(before + 1);
      faults.restore();
      var replay = post(filtered(), key);
      assertThat(replay.statusCode()).isEqualTo(200);
      assertThat(count("export_tasks")).isEqualTo(before + 1);
    } finally {
      faults.restore();
    }
  }

  @Test
  void TC218_successResponseIsReadableOnSeparateConnectionWithIdempotencyAndOutbox()
      throws Exception {
    String key = newKey();
    var response = post(filtered(), key);
    assertThat(response.statusCode()).isEqualTo(201);
    String id = body(response).get("taskId").asText();
    try (var connection =
            java.sql.DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var statement =
            connection.prepareStatement(
                "SELECT t.task_id FROM export_tasks t JOIN command_outbox o ON t.task_id=o.task_id JOIN export_idempotency i ON t.task_id=i.task_id WHERE t.task_id=?")) {
      statement.setString(1, id);
      var rows = statement.executeQuery();
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo(id);
      assertThat(rows.next()).isFalse();
    }
    var replay = post(filtered(), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay).get("taskId").asText()).isEqualTo(id);
  }

  @Test
  void TC218_outboxInsertFailureRollsBackAllCreationRecords() throws Exception {
    long tasks = count("export_tasks"), keys = count("export_idempotency");
    try (var setup = mysql.getAdminConnection();
        var ddl = setup.createStatement()) {
      ddl.execute(
          "CREATE TRIGGER synthetic_fail_outbox BEFORE INSERT ON command_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic-test-failure'");
      try {
        var response = post(filtered(), newKey());
        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(count("export_tasks")).isEqualTo(tasks);
        assertThat(count("export_idempotency")).isEqualTo(keys);
        assertThat(response.body()).doesNotContain("synthetic-test-failure", "SQLSTATE");
      } finally {
        ddl.execute("DROP TRIGGER synthetic_fail_outbox");
      }
    }
  }
}
