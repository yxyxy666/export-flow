// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.progress;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProgressRedisFixture;
import com.exportflow.testing.ProgressRedisFixture.RedisTestSettings;
import com.exportflow.testing.TaskManagementFixture;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// [AC-039](../../../../../../../docs/product/acceptance-criteria.md#ac-039)
// [AC-042](../../../../../../../docs/product/acceptance-criteria.md#ac-042)
// [AC-043](../../../../../../../docs/product/acceptance-criteria.md#ac-043)
// [AC-050](../../../../../../../docs/product/acceptance-criteria.md#ac-050)
// [AC-081](../../../../../../../docs/product/acceptance-criteria.md#ac-081)
// [AC-082](../../../../../../../docs/product/acceptance-criteria.md#ac-082)
// [AC-085](../../../../../../../docs/product/acceptance-criteria.md#ac-085)
class TaskProgressIntegrationTest extends TaskManagementFixture {
  private static final String PROGRESS = "com.exportflow.progress.port.ProgressRepository";
  private final String prefix = "export-flow:test:" + UUID.randomUUID() + ":";
  private String instanceId = "test-004-" + UUID.randomUUID();

  @Override
  protected List<String> applicationArguments() {
    return List.of(
        "--export-flow.progress-cache-prefix=" + prefix, "--export-flow.instance-id=" + instanceId);
  }

  private ProgressRedisFixture redis() throws IOException {
    return ProgressRedisFixture.connect(
        new RedisTestSettings(
            "127.0.0.1",
            6379,
            System.getenv("EXPORT_FLOW_TEST_REDIS_USER"),
            System.getenv("EXPORT_FLOW_TEST_REDIS_PASSWORD"),
            prefix));
  }

  @Test
  void TC406_realRedisAcceptsNewerLowerProgressAndRejectsMissingStaleOrCorruptReads()
      throws Exception {
    var task = create();
    String cachePort = "com.exportflow.progress.port.ProgressCache";
    var original = port("com.exportflow.exporttasks.port.TaskManagementRepository", "find", task);
    try (var redis = redis()) {
      assertThat((Optional<?>) port(cachePort, "get", task, 0L)).isEmpty();
      var high =
          copy(
              original,
              Map.of("version", 9007199254740993L, "processedCount", 4L, "progressPercent", 99));
      port(cachePort, "putIfNewer", high);
      redis.get(task); // 登记生产适配器创建的键，关闭夹具时精确回收。
      assertThat((Optional<?>) port(cachePort, "get", task, 9007199254740994L)).isEmpty();
      var lower =
          copy(
              original,
              Map.of("version", 9007199254740994L, "processedCount", 2L, "progressPercent", 50));
      port(cachePort, "putIfNewer", lower);
      var accepted = ((Optional<?>) port(cachePort, "get", task, 9007199254740994L)).orElseThrow();
      assertThat(property(accepted, "processedCount")).isEqualTo(2L);
      assertThat(property(accepted, "version")).isEqualTo(9007199254740994L);
      redis.seed(task, "{broken-json");
      assertThat((Optional<?>) port(cachePort, "get", task, 0L)).isEmpty();
    }
  }

  @Test
  void TC406_sameStableInstanceIdCannotStartSecondConsumer() throws Exception {
    var original = app;
    var originalBase = base;
    try {
      assertThatThrownBy(
              () -> startApplication(Class.forName("com.exportflow.ExportFlowApplication")))
          .isInstanceOf(RuntimeException.class);
    } finally {
      if (app != original) app.close();
      app = original;
      base = originalBase;
    }
  }

  @Test
  void TC406_existingRedisFixtureWritesAndCleansOnlyItsOwnKeys() throws Exception {
    var id = UUID.randomUUID();
    try (var redis = redis()) {
      redis.seed(id, "{\"version\":\"1\"}");
      assertThat(redis.get(id)).contains("\"1\"");
    }
    try (var redis = redis()) {
      assertThat(redis.get(id)).isNull();
    }
  }

  @Test
  void TC406_realRedisRejectsOlderSummaryWithoutIncreasingProgress() throws Exception {
    var task = create();
    var summary = listedTask(task).deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) summary).put("version", "9007199254740993");
    try (var cache = redis()) {
      cache.seed(
          task, json.writeValueAsString(Map.of("version", "9007199254740993", "task", summary)));
      call("com.exportflow.progress.application.TaskProgressService", "dispatchPending", 20);
      var entry = json.readTree(cache.get(task));
      assertThat(entry.path("version").asText()).isEqualTo("9007199254740993");
      assertThat(entry.path("task").path("processedCount").asLong()).isZero();
    }
  }

  @Test
  @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void TC411_fanoutDeliversToTwoIndependentApiInstances() throws Exception {
    assertThat(
            app.getBean(
                    org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class)
                .getListenerContainer("task-progress"))
        .isNotNull();
    var task = create();
    var firstApp = app;
    var firstBase = base;
    var first =
        http.send(
            HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/events")).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var input = first.body();
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      assertThat(first.statusCode()).isEqualTo(200);
      nextEvent(reader);
      instanceId = "test-004-second-" + UUID.randomUUID();
      startApplication(Class.forName("com.exportflow.ExportFlowApplication"));
      var second =
          http.send(
              HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/events")).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (var input2 = second.body();
          var reader2 = new BufferedReader(new InputStreamReader(input2, StandardCharsets.UTF_8));
          var cache = redis()) {
        assertThat(second.statusCode()).isEqualTo(200);
        nextEvent(reader2);
        collect(task, lease(task));
        call("com.exportflow.progress.application.TaskProgressService", "dispatchPending", 20);
        assertThat(nextEvent(reader).get("event")).isEqualTo("task-updated");
        assertThat(nextEvent(reader2).get("event")).isEqualTo("task-updated");
        cache.get(task);
      }
    } finally {
      if (app != firstApp) app.close();
      app = firstApp;
      base = firstBase;
    }
  }

  @Test
  void TC406_committedTaskAndPendingNoticeShareVersionThenPopulateVersionedRedis()
      throws Exception {
    var task = create();
    var summary = listedTask(task);
    assertThat(
            db.queryForObject(
                "SELECT pending_version FROM task_progress_outbox WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(summary.path("version").asLong());
    try (var cache = redis()) {
      call("com.exportflow.progress.application.TaskProgressService", "dispatchPending", 20);
      var entry = json.readTree(Objects.requireNonNull(cache.get(task), "提交进度应写入Redis"));
      assertThat(entry.path("version").asText()).isEqualTo(summary.path("version").asText());
      assertThat(entry.path("task").path("processedCount").asLong()).isZero();
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM command_outbox WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isEqualTo(1);
    }
  }

  @Test
  void TC406_rolledBackBusinessChangeCannotLeaveActivityOrNotification() throws Exception {
    var task = create();
    var before =
        db.queryForMap("SELECT version FROM export_tasks WHERE task_id=?", task.toString());
    var activity = db.queryForMap("SELECT version FROM task_activity WHERE id=1");
    var notice =
        db.queryForMap("SELECT * FROM task_progress_outbox WHERE task_id=?", task.toString());
    var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
    var transactionalDb =
        new org.springframework.jdbc.core.JdbcTemplate(app.getBean(javax.sql.DataSource.class));
    var synthetic = new IllegalStateException("synthetic rollback");
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      try {
                        port(PROGRESS, "lockActivity");
                        transactionalDb.update(
                            "UPDATE export_tasks SET version=version+1 WHERE task_id=?",
                            task.toString());
                        var version =
                            transactionalDb.queryForObject(
                                "SELECT version FROM export_tasks WHERE task_id=?",
                                Long.class,
                                task.toString());
                        port(PROGRESS, "recordChange", task, version);
                      } catch (Exception failure) {
                        throw new AssertionError("真实通知事务接口不可调用", failure);
                      }
                      throw synthetic;
                    }))
        .isSameAs(synthetic);
    assertThat(db.queryForMap("SELECT version FROM export_tasks WHERE task_id=?", task.toString()))
        .isEqualTo(before);
    assertThat(db.queryForMap("SELECT version FROM task_activity WHERE id=1")).isEqualTo(activity);
    assertThat(
            db.queryForMap("SELECT * FROM task_progress_outbox WHERE task_id=?", task.toString()))
        .isEqualTo(notice);
  }

  @Test
  void TC406_oldPublishConfirmationCannotEraseNewerPendingVersion() throws Exception {
    var task = create();
    var leases = (List<?>) port(PROGRESS, "claimPending", "test-notice-owner", 20);
    var noticeLease =
        leases.stream()
            .filter(item -> property(item, "taskId").equals(task))
            .findFirst()
            .orElseThrow();
    var oldVersion = ((Number) property(noticeLease, "pendingVersion")).longValue();
    collect(task, lease(task));
    var proofType = method(PROGRESS, "markPublished").getParameterTypes()[1];
    var proof = record(proofType, Map.of("confirmAck", true, "correctRoute", true));
    port(PROGRESS, "markPublished", noticeLease, proof);
    var row =
        db.queryForMap(
            "SELECT pending_version,published_version FROM task_progress_outbox WHERE task_id=?",
            task.toString());
    assertThat(((Number) row.get("pending_version")).longValue()).isGreaterThan(oldVersion);
    assertThat(((Number) row.get("published_version")).longValue()).isLessThanOrEqualTo(oldVersion);
  }

  @Test
  void TC411_activityCommittedBetweenIdleReadsCancelsOldIdleCandidate() throws Exception {
    var candidate = port(PROGRESS, "prepareIdle");
    assertThat(port(PROGRESS, "confirmIdle", candidate)).isEqualTo(true);
    create();
    assertThat(port(PROGRESS, "confirmIdle", candidate)).isEqualTo(false);
    assertThat(property(port(PROGRESS, "prepareIdle"), "hasActiveTasks")).isEqualTo(true);
  }

  @Test
  void TC406_failedRealPublicationRetainsNoticeAndOriginalCommand() throws Exception {
    var task = create();
    var original = outbox(task);
    probe.on(
        "com.exportflow.progress.port.ProgressPublisher",
        "publish",
        (method, args, real) -> {
          throw new IOException("synthetic network boundary failure");
        });
    try {
      call("com.exportflow.progress.application.TaskProgressService", "dispatchPending", 20);
    } finally {
      probe.clear();
    }
    assertThat(probe.calls("com.exportflow.progress.port.ProgressPublisher", "publish"))
        .isPositive();
    var notice =
        db.queryForMap(
            "SELECT pending_version,published_version FROM task_progress_outbox WHERE task_id=?",
            task.toString());
    assertThat(((Number) notice.get("published_version")).longValue())
        .isLessThan(((Number) notice.get("pending_version")).longValue());
    assertThat(outbox(task)).isEqualTo(original);
    assertThat(state(task)).isEqualTo("PENDING");
  }

  @Test
  // 仅防止缺少事件时挂住运行器；不验证产品超时或延时。
  @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void TC411_realSseSendsNamedReadyAndTaskUpdateAfterDurableDispatch() throws Exception {
    assertThat(
            app.getBean(
                    org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry.class)
                .getListenerContainer("task-progress"))
        .isNotNull();
    var task = create();
    var response =
        http.send(
            HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/events")).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = response.body();
        var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type").orElse(""))
          .startsWith("text/event-stream");
      var ready = nextEvent(reader);
      assertThat(ready.get("event")).isEqualTo("stream-ready");
      assertThat(json.readTree(ready.get("data")).hasNonNull("connectionId")).isTrue();
      var lease = lease(task);
      collect(task, lease);
      call("com.exportflow.progress.application.TaskProgressService", "dispatchPending", 20);
      var update = nextEvent(reader);
      assertThat(update.get("event")).isEqualTo("task-updated");
      assertThat(json.readTree(update.get("data")).path("task").path("taskId").asText())
          .isEqualTo(task.toString());
    }
  }

  private Map<String, String> nextEvent(BufferedReader reader) throws IOException {
    var result = new LinkedHashMap<String, String>();
    for (String line; (line = reader.readLine()) != null; ) {
      if (line.isEmpty() && result.containsKey("event")) return result;
      if (line.startsWith("event:")) result.put("event", line.substring(6).strip());
      if (line.startsWith("data:"))
        result.merge("data", line.substring(5).strip(), (a, b) -> a + "\n" + b);
    }
    throw new EOFException("真实SSE未发送预期具名事件");
  }
}
