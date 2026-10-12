// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// [AC-013](../../../../../../../docs/product/acceptance-criteria.md#ac-013)
// [AC-014](../../../../../../../docs/product/acceptance-criteria.md#ac-014)
// [AC-018](../../../../../../../docs/product/acceptance-criteria.md#ac-018)
class DownloadBoundaryTest extends BoundaryFixture {
  private UUID complete() throws Exception {
    var task = create();
    var lease = lease(task);
    assertThat(call(EXEC_SERVICE, "complete", lease, generate(task, lease)).toString())
        .isEqualTo("APPLIED");
    return task;
  }

  private Object tracked(
      Object handle, AtomicBoolean closed, java.util.function.Consumer<Integer> beforeRead)
      throws Exception {
    InputStream original = (InputStream) property(handle, "input");
    var reads = new AtomicInteger();
    var stream =
        new FilterInputStream(original) {
          @Override
          public int read(byte[] bytes, int offset, int length) throws IOException {
            assertThat(
                    org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive())
                .isFalse();
            beforeRead.accept(reads.incrementAndGet());
            return super.read(bytes, offset, Math.min(length, 1024));
          }

          @Override
          public void close() throws IOException {
            closed.set(true);
            super.close();
          }
        };
    Object metadata = property(handle, "metadata");
    var ctor = handle.getClass().getDeclaredConstructor(metadata.getClass(), InputStream.class);
    ctor.trySetAccessible();
    return ctor.newInstance(metadata, stream);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RUNNING", "FAILED"})
  void TC234_everyNonSuccessStateRejectsDownload(String state) throws Exception {
    var task = create();
    var lease = lease(task);
    if (state.equals("FAILED"))
      call(EXEC_SERVICE, "complete", lease, failure("EXPORT_CELL_INVALID"));
    var response = get("/api/v1/export-tasks/" + task + "/file");
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(body(response).get("code").asText()).isEqualTo("TASK_NOT_DOWNLOADABLE");
    assertThat(response.body()).doesNotContain(root.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"database", "permission", "size"})
  void TC234_downloadDependencyAndMetadataErrorsAreSafe(String defect) throws Exception {
    var task = complete();
    if (defect.equals("database"))
      probe.on(
          TASKS,
          "findSnapshot",
          (m, a, real) -> {
            throw new java.sql.SQLTransientConnectionException("synthetic-secret-SQL");
          });
    if (defect.equals("permission"))
      probe.on(
          STORAGE,
          "openDownload",
          (m, a, real) -> {
            throw new AccessDeniedException(root.toString());
          });
    if (defect.equals("size")) {
      String artifact =
          db.queryForObject(
              "SELECT result_artifact_id FROM export_tasks WHERE task_id=?",
              String.class,
              task.toString());
      Files.writeString(
          root.resolve("tasks")
              .resolve(task.toString())
              .resolve("artifacts")
              .resolve(artifact + ".xlsx"),
          "truncated synthetic file");
    }
    var response = get("/api/v1/export-tasks/" + task + "/file");
    assertThat(response.statusCode()).isEqualTo(defect.equals("database") ? 503 : 500);
    assertThat(response.body()).doesNotContain(root.toString(), "synthetic-secret-SQL", "java.");
    assertThat(state(task)).isEqualTo("SUCCEEDED");
  }

  @Test
  void TC234_handleFailureAutomaticallyClosesInputAndDoesNotCloseFrameworkOutput()
      throws Exception {
    var task = complete();
    var closed = new AtomicBoolean();
    probe.on(STORAGE, "openDownload", (m, a, real) -> tracked(real.run(), closed, n -> {}));
    Object handle =
        call("com.exportflow.exporttasks.application.DownloadExportFileService", "prepare", task);
    var output = org.mockito.Mockito.mock(OutputStream.class);
    org.mockito.Mockito.doThrow(new IOException("synthetic disconnect"))
        .when(output)
        .write(
            org.mockito.ArgumentMatchers.any(byte[].class),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
    assertThatThrownBy(
            () -> handle.getClass().getMethod("writeTo", OutputStream.class).invoke(handle, output))
        .hasCauseInstanceOf(IOException.class);
    assertThat(closed).isTrue();
    org.mockito.Mockito.verify(output, org.mockito.Mockito.never()).close();
    ((AutoCloseable) handle).close();
    ((AutoCloseable) handle).close();
    assertThat(state(task)).isEqualTo("SUCCEEDED");
  }

  @Test
  void TC234_realHttpDisconnectClosesStreamOutsideTransactionAndKeepsSuccess() throws Exception {
    useOrderVolume(10001);
    var task = createVolume();
    var lease = lease(task);
    call(EXEC_SERVICE, "complete", lease, generate(task, lease));
    var closed = new AtomicBoolean();
    try (var gate = new BoundaryProbe.Gate()) {
      probe.on(
          STORAGE,
          "openDownload",
          (m, a, real) ->
              tracked(
                  real.run(),
                  closed,
                  n -> {
                    if (n == 16)
                      try {
                        gate.pause();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                      }
                  }));
      try (var socket = new java.net.Socket(base.getHost(), base.getPort())) {
        socket.setSoTimeout(10000);
        socket
            .getOutputStream()
            .write(
                ("GET /api/v1/export-tasks/"
                        + task
                        + "/file HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        assertThat(socket.getInputStream().read()).isNotEqualTo(-1);
        gate.await();
        socket.setSoLinger(true, 0);
      }
      gate.close();
      org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilTrue(closed);
    }
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    probe.clear();
    assertThat(download(task)).isNotEmpty();
  }

  @Test
  void TC234_symlinkOrWindowsJunctionCannotEscapeConfiguredRoot() throws Exception {
    var task = complete();
    String artifact =
        db.queryForObject(
            "SELECT result_artifact_id FROM export_tasks WHERE task_id=?",
            String.class,
            task.toString());
    Path actual = root.resolve("tasks").resolve(task.toString()).resolve("artifacts");
    Path moved = root.resolve("protected-artifacts");
    Files.move(actual, moved);
    // 两个路径均在测试新建root内；Windows用junction无需提升权限，清理仅删链接本身。
    boolean windows = System.getProperty("os.name").startsWith("Windows");
    if (windows) {
      var process =
          new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", actual.toString(), moved.toString())
              .redirectErrorStream(true)
              .start();
      String log = new String(process.getInputStream().readAllBytes());
      assertThat(process.waitFor()).as(log).isZero();
    } else Files.createSymbolicLink(actual, moved);
    try {
      var response = get("/api/v1/export-tasks/" + task + "/file");
      assertThat(response.statusCode()).isBetween(400, 599);
      assertThat(response.body()).doesNotContain(root.toString());
      assertThat(moved.resolve(artifact + ".xlsx")).isRegularFile();
    } finally {
      Files.delete(actual);
      Files.move(moved, actual);
    }
    assertThat(download(task)).isNotEmpty();
  }

  @Test
  void TC234_restartKeepsFileAndOriginalIdempotencyResult() throws Exception {
    String key = newKey();
    var response = post(filtered(), key);
    var task = UUID.fromString(body(response).get("taskId").asText());
    var lease = lease(task);
    call(EXEC_SERVICE, "complete", lease, generate(task, lease));
    byte[] before = download(task);
    restartApplication();
    var replay = post(filtered(), key);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(body(replay)).isEqualTo(body(response));
    assertThat(download(task)).isEqualTo(before);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"../escape", "..\\escape", "C:\\outside", "/absolute"})
  void TC234_serverLogicalReferenceRejectsTraversalAndAbsoluteOwnerSegments(String owner)
      throws Exception {
    var task = create();
    Class<?> type = method(STORAGE, "resolve").getParameterTypes()[0];
    assertThatThrownBy(
            () -> {
              Object ref =
                  record(
                      type,
                      Map.of(
                          "kind",
                          "STAGING",
                          "taskId",
                          task,
                          "identity",
                          java.util.UUID.randomUUID(),
                          "attemptFence",
                          1L,
                          "owner",
                          owner));
              port(STORAGE, "resolve", ref);
            })
        .isInstanceOf(Exception.class);
    assertThat(count("export_artifacts")).isZero();
  }
}
