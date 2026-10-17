// [规格与证据](../../../../../../../specs/005-task-resume-and-live-progress/tasks.md)
package com.exportflow.progress;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProgressRedisFixture;
import com.exportflow.testing.ProgressRedisFixture.RedisTestSettings;
import com.exportflow.testing.TaskManagementFixture;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// [AC-094](../../../../../../../docs/product/acceptance-criteria.md#ac-094)
class AutomaticTaskProgressTest extends TaskManagementFixture {
  private final String prefix = "export-flow:test:" + UUID.randomUUID() + ":";

  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Override
  protected List<String> applicationArguments() {
    return List.of(
        "--export-flow.progress-cache-prefix=" + prefix,
        "--export-flow.instance-id=test-005-" + UUID.randomUUID());
  }

  private JsonNode nextTask(BufferedReader reader) throws Exception {
    String event = "";
    for (String line; (line = reader.readLine()) != null; ) {
      if (line.startsWith("event:")) event = line.substring(6).trim();
      if (line.startsWith("data:") && event.equals("task-updated"))
        return json.readTree(line.substring(5).trim()).path("task");
      if (event.equals("stream-idle")) throw new AssertionError("最终任务事件前通道已空闲关闭");
    }
    throw new EOFException("未收到任务事件");
  }

  @Test
  @Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void TC505_productionAutomaticallyStreamsCommittedIntermediateAndAdoptedHundredPercent()
      throws Exception {
    assertThat(
            app.containsBean(
                "org.springframework.context.annotation.internalScheduledAnnotationProcessor"))
        .as("生产入口必须启用Spring调度，不能靠测试手动调用dispatch")
        .isTrue();
    useThreeSmallBlocks();
    var firstBatch = new CountDownLatch(1);
    var start = new CountDownLatch(1);
    var secondBatch = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var batches = new AtomicInteger();
    probe.on(
        ORDERS,
        "readBatch",
        (m, a, real) -> {
          a[3] = 2;
          int batch = batches.incrementAndGet();
          if (batch == 1) {
            firstBatch.countDown();
            if (!start.await(60, TimeUnit.SECONDS)) throw new AssertionError("初始屏障未解除");
          }
          if (batch == 2) {
            secondBatch.countDown();
            if (!release.await(60, TimeUnit.SECONDS)) throw new AssertionError("测试屏障未解除");
          }
          return real.run();
        });
    UUID task = null;
    try (var cache =
        ProgressRedisFixture.connect(
            new RedisTestSettings(
                "127.0.0.1",
                6379,
                System.getenv("EXPORT_FLOW_TEST_REDIS_USER"),
                System.getenv("EXPORT_FLOW_TEST_REDIS_PASSWORD"),
                prefix))) {
      try {
        task = createVolume();
        assertThat(firstBatch.await(30, TimeUnit.SECONDS)).as("真实worker已自动开始").isTrue();
        var response =
            http.send(
                HttpRequest.newBuilder(base.resolve("/api/v1/export-tasks/events")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        try (var input = response.body();
            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
          start.countDown();
          assertThat(secondBatch.await(30, TimeUnit.SECONDS)).as("真实worker已提交第一块").isTrue();
          JsonNode middle;
          do {
            middle = nextTask(reader);
          } while (middle.path("processedCount").asLong() != 2);
          assertThat(middle.path("status").asText()).isEqualTo("RUNNING");
          assertThat(middle.path("progressPercent").asInt()).isEqualTo(33);
          assertThat(json.readTree(cache.get(task)).path("task").path("processedCount").asLong())
              .isEqualTo(2);
          release.countDown();
          JsonNode completed;
          do {
            completed = nextTask(reader);
          } while (!completed.path("status").asText().equals("SUCCEEDED"));
          assertThat(completed.path("progressPercent").asInt()).isEqualTo(100);
          assertThat(completed.path("allowedActions").toString()).contains("DOWNLOAD");
          assertThat(listedTask(task).path("progressPercent").asInt()).isEqualTo(100);
          assertThat(
                  db.queryForObject(
                      "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                      Long.class,
                      task.toString()))
              .isEqualTo(1);
          assertThat(get("/api/v1/export-tasks/" + task + "/file").statusCode()).isEqualTo(200);
        }
      } finally {
        start.countDown();
        release.countDown();
        // 先停止本测试应用，再清理其缓存键，避免调度线程重新创建。
        app.close();
        if (task != null) cache.get(task);
      }
    }
  }
}
