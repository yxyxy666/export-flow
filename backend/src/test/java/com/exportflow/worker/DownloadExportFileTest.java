// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// [AC-013](../../../../../../../docs/product/acceptance-criteria.md#ac-013)
// [AC-014](../../../../../../../docs/product/acceptance-criteria.md#ac-014)
// [AC-018](../../../../../../../docs/product/acceptance-criteria.md#ac-018)
// [AC-067](../../../../../../../docs/product/acceptance-criteria.md#ac-067)
class DownloadExportFileTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Test
  void TC234_absentTaskReturns404AndPendingTaskReturns409WithoutPhysicalPath() throws Exception {
    var missing = get("/api/v1/export-tasks/" + UUID.randomUUID() + "/file");
    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(body(missing).get("code").asText()).isEqualTo("TASK_NOT_FOUND");
    var pending = get("/api/v1/export-tasks/" + create() + "/file");
    assertThat(pending.statusCode()).isEqualTo(409);
    assertThat(body(pending).get("code").asText()).isEqualTo("TASK_NOT_DOWNLOADABLE");
    assertThat(pending.body()).doesNotContain(root.toString(), "stackTrace", "java.io");
  }

  @Test
  void TC234_missingSuccessfulFileReturns410WithoutRerunningOrChangingTerminalState()
      throws Exception {
    var task = create();
    awaitSuccess(task);
    String artifact =
        db.queryForObject(
            "SELECT result_artifact_id FROM export_tasks WHERE task_id=?",
            String.class,
            task.toString());
    Path file =
        root.resolve("tasks")
            .resolve(task.toString())
            .resolve("artifacts")
            .resolve(artifact + ".xlsx");
    assertThat(file).isRegularFile();
    Files.delete(file);
    var response = get("/api/v1/export-tasks/" + task + "/file");
    assertThat(response.statusCode()).isEqualTo(410);
    assertThat(body(response).get("code").asText()).isEqualTo("EXPORT_FILE_MISSING");
    assertThat(response.body()).doesNotContain(root.toString(), "stackTrace");
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertThat(file).doesNotExist();
    assertThat(count("command_outbox")).isEqualTo(1);
    assertThat(count("export_artifacts")).isEqualTo(1);
  }

  @Test
  void TC234_downloadWriteFailurePropagatesIoAndKeepsCommittedSuccess() throws Exception {
    var task = create();
    awaitSuccess(task);
    Object handle =
        call("com.exportflow.exporttasks.application.DownloadExportFileService", "prepare", task);
    assertThat(handle).isInstanceOf(AutoCloseable.class);
    try {
      var output =
          new java.io.OutputStream() {
            @Override
            public void write(int value) throws java.io.IOException {
              throw new java.io.IOException("synthetic client disconnect");
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws java.io.IOException {
              throw new java.io.IOException("synthetic client disconnect");
            }
          };
      assertThatThrownBy(
              () ->
                  handle
                      .getClass()
                      .getMethod("writeTo", java.io.OutputStream.class)
                      .invoke(handle, output))
          .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
          .hasCauseInstanceOf(java.io.IOException.class);
    } finally {
      ((AutoCloseable) handle).close();
    }
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertThat(download(task)).isNotEmpty();
    assertThat(count("command_outbox")).isEqualTo(1);
    // 此断言覆盖句柄边界；控制器实际断连的关闭行为仍需真实HTTP验证。
  }
}
