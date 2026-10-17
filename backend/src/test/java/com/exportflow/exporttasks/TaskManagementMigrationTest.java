// 设计：[迁移与兼容](../../../../../../../specs/004-task-management/plan.md)。
// [AC-087](../../../../../../../docs/product/acceptance-criteria.md#ac-087)
// [AC-088](../../../../../../../docs/product/acceptance-criteria.md#ac-088)
package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.exportflow.testing.SharedServices;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 每个版本路径使用独立真实MySQL；不修改来源订单或已发布迁移。 */
class TaskManagementMigrationTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 4, 5})
  void TC409_emptyAndSupportedUpgradePreserveTasksAndBackfillTrustedProgress(int previous)
      throws Exception {
    try (var mysql = SharedServices.mysql()) {
      mysql.start();
      var id = UUID.randomUUID().toString();
      var commandId = UUID.randomUUID().toString();
      var blockId = UUID.randomUUID().toString();
      var readyId = UUID.randomUUID().toString();
      var successfulId = UUID.randomUUID().toString();
      var adoptedId = UUID.randomUUID().toString();
      if (previous > 0) {
        var old =
            Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target(String.valueOf(previous))
                .load();
        old.migrate();
        assertThat(old.info().current().getVersion().toString())
            .as("支持的前序迁移存在")
            .isEqualTo(String.valueOf(previous));
        try (var connection = mysql.getAdminConnection();
            var statement =
                connection.prepareStatement(
                    "INSERT INTO export_tasks(task_id,task_no,scope_type,filter_json,fields_json,total_count,processed_count,status,version,created_at) VALUES(?,?,'FILTERED','{}','[\"orderNo\"]',6,4,'RUNNING',7,'2026-10-17 00:00:00')")) {
          statement.setString(1, id);
          statement.setString(2, "EXP_MIGRATION");
          statement.executeUpdate();
        }
        try (var connection = mysql.getAdminConnection();
            var statement = connection.createStatement()) {
          statement.executeUpdate(
              "INSERT INTO command_outbox(event_id,task_id,type,status,accepted_at,dispatch_deadline,next_attempt_at) VALUES('"
                  + commandId
                  + "','"
                  + id
                  + "','EXPORT','SENT','2026-10-17','2026-10-18','2026-10-17')");
          statement.executeUpdate(
              "INSERT INTO export_checkpoints(task_id,data_generation,checkpoint_version,verified_prefix_count,processed_high_water,configuration_hash,format_version) VALUES('"
                  + id
                  + "',1,3,4,4,REPEAT('a',64),1)");
          statement.executeUpdate(
              "INSERT INTO export_data_blocks(block_id,task_id,data_generation,sequence,last_cursor_json,row_count,byte_size,checksum,storage_ref,format_version,configuration_hash,created_by_owner,created_by_fence) VALUES('"
                  + blockId
                  + "','"
                  + id
                  + "',1,1,'{}',4,100,REPEAT('b',64),'migration-block-ref',1,REPEAT('a',64),'migration-owner',1)");
          statement.executeUpdate(
              "INSERT INTO export_artifacts(artifact_id,task_id,data_generation,configuration_hash,storage_ref,row_count,byte_size,checksum,file_name,state,created_by_owner,created_by_fence,ready_at) VALUES('"
                  + readyId
                  + "','"
                  + id
                  + "',1,REPEAT('a',64),'migration-ready-ref',6,200,REPEAT('c',64),'ready.xlsx','READY','migration-owner',1,'2026-10-17')");
          statement.executeUpdate(
              "INSERT INTO export_tasks(task_id,task_no,scope_type,filter_json,fields_json,total_count,processed_count,status,version,created_at) VALUES('"
                  + successfulId
                  + "','EXP_MIGRATION_SUCCESS','FILTERED','{}','[\"orderNo\"]',6,6,'RUNNING',8,'2026-10-17')");
          statement.executeUpdate(
              "INSERT INTO export_artifacts(artifact_id,task_id,data_generation,configuration_hash,storage_ref,row_count,byte_size,checksum,file_name,state,created_by_owner,created_by_fence,ready_at,adopted_by_fence) VALUES('"
                  + adoptedId
                  + "','"
                  + successfulId
                  + "',1,REPEAT('a',64),'migration-adopted-ref',6,300,REPEAT('d',64),'success.xlsx','ADOPTED','migration-owner',1,'2026-10-17',1)");
          statement.executeUpdate(
              "UPDATE export_tasks SET status='SUCCEEDED',finished_at='2026-10-17',result_artifact_id='"
                  + adoptedId
                  + "',file_name='success.xlsx',file_size=300 WHERE task_id='"
                  + successfulId
                  + "'");
          if (previous == 5) {
            var paused = UUID.randomUUID().toString();
            statement.executeUpdate(
                "INSERT INTO export_tasks(task_id,task_no,scope_type,fields_json,total_count,status,created_at) VALUES('"
                    + paused
                    + "','EXP_MIGRATION_PAUSED','FILTERED','[\"orderNo\"]',6,'PAUSED','2026-10-17')");
          }
        }
      }
      var latest =
          Flyway.configure()
              .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
              .load();
      latest.migrate();
      assertThat(latest.info().current().getVersion().toString()).isEqualTo("6");
      try (var connection = mysql.getAdminConnection();
          var statement = connection.createStatement()) {
        if (previous > 0)
          try (var rows =
              statement.executeQuery(
                  "SELECT processed_count,trusted_processed_count,version,control_version,status FROM export_tasks WHERE task_id='"
                      + id
                      + "'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(4);
            // 非终态旧计数未经新协议实体验证，必须从0开始，不能迁移为可信。
            assertThat(rows.getLong(2)).isZero();
            assertThat(rows.getLong(3)).isEqualTo(7);
            assertThat(rows.getLong(4)).isZero();
            assertThat(rows.getString(5)).isEqualTo("RUNNING");
          }
        try (var rows = statement.executeQuery("SELECT version FROM task_activity WHERE id=1")) {
          assertThat(rows.next()).isTrue();
        }
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM export_data_segments")) {
          rows.next();
          assertThat(rows.getLong(1)).isZero();
        }
        if (previous > 0) {
          for (var query :
              java.util.List.of(
                  "SELECT event_id FROM command_outbox WHERE task_id='"
                      + id
                      + "' AND status='SENT' AND schema_version=1",
                  "SELECT block_id FROM export_data_blocks WHERE task_id='"
                      + id
                      + "' AND storage_ref='migration-block-ref' AND checksum=REPEAT('b',64)",
                  "SELECT artifact_id FROM export_artifacts WHERE task_id='"
                      + id
                      + "' AND state='READY' AND storage_ref='migration-ready-ref'",
                  "SELECT result_artifact_id FROM export_tasks WHERE task_id='"
                      + successfulId
                      + "' AND status='SUCCEEDED' AND file_name='success.xlsx' AND file_size=300 AND trusted_processed_count=6",
                  "SELECT task_id FROM export_checkpoints WHERE task_id='"
                      + id
                      + "' AND checkpoint_version=3 AND verified_prefix_count=4 AND manifest_sealed=FALSE")) {
            try (var rows = statement.executeQuery(query)) {
              assertThat(rows.next()).isTrue();
            }
          }
          if (previous == 5)
            try (var rows =
                statement.executeQuery(
                    "SELECT task_id FROM export_tasks WHERE task_no='EXP_MIGRATION_PAUSED' AND status='PAUSED' AND trusted_processed_count=0")) {
              assertThat(rows.next()).isTrue();
            }
        }
      }
    }
  }
}
