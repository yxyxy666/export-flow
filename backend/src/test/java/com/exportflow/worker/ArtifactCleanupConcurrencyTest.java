// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

// [AC-018](../../../../../../../docs/product/acceptance-criteria.md#ac-018)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
class ArtifactCleanupConcurrencyTest extends BoundaryFixture {
  private static final String CLEANUP = "com.exportflow.worker.application.OrphanCleanupService";

  @Test
  void TC236_readyAdoptionAndCleanupCompeteUnderRealDatabaseLocks() throws Exception {
    var task = create();
    var old = lease(task);
    var result = generate(task, old);
    var proof = property(result, "artifact");
    expireExecution(task);
    var next = property(claim(task, "synthetic-adopter"), "lease");
    Class<?> type = method(ARTIFACT, "claimCleanup").getParameterTypes()[0];
    var candidate =
        record(
            type,
            Map.of(
                "id",
                UUID.randomUUID(),
                "ref",
                property(proof, "artifactRef"),
                "taskId",
                task,
                "observedAt",
                Instant.now().minus(Duration.ofDays(2))));
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
        var blocker = java.util.Objects.requireNonNull(db.getDataSource()).getConnection()) {
      blocker.setAutoCommit(false);
      try (var sql =
          blocker.prepareStatement(
              "SELECT artifact_id FROM export_artifacts WHERE task_id=? FOR UPDATE")) {
        sql.setString(1, task.toString());
        sql.executeQuery().close();
      }
      var start = new CyclicBarrier(2);
      var adopted =
          threads.submit(
              () -> {
                start.await();
                return call(EXEC_SERVICE, "complete", next, result);
              });
      var cleaned =
          threads.submit(
              () -> {
                start.await();
                return port(ARTIFACT, "claimCleanup", candidate, Instant.now());
              });
      // 确认生产事务在真实MySQL锁上等待，才释放，避免把顺序调用命名为竞态。
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                try (var observer = mysql.getAdminConnection();
                    var sql = observer.createStatement();
                    var rows =
                        sql.executeQuery(
                            "SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                  rows.next();
                  assertThat(rows.getLong(1)).isPositive();
                }
              });
      blocker.commit();
      assertThat(adopted.get(20, TimeUnit.SECONDS).toString()).isEqualTo("APPLIED");
      assertThat((Optional<?>) cleaned.get(20, TimeUnit.SECONDS)).isEmpty();
    }
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertThat(download(task)).isNotEmpty();
  }

  @Test
  void TC236_runningCheckpointAndReadySurviveGraceAndExpiredLease() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    var blocks =
        db.queryForList(
            "SELECT block_id,checksum FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
            task.toString());
    db.update(
        "UPDATE export_artifacts SET ready_at=UTC_TIMESTAMP()-INTERVAL 48 HOUR WHERE task_id=?",
        task.toString());
    expireExecution(task);
    call(CLEANUP, "scanCandidates", 20);
    assertThat(state(task)).isEqualTo("RUNNING");
    assertThat(
            db.queryForList(
                "SELECT block_id,checksum FROM export_data_blocks WHERE task_id=? ORDER BY sequence",
                task.toString()))
        .isEqualTo(blocks);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='READY'",
                Long.class,
                task.toString()))
        .isEqualTo(1);
    var next = property(claim(task, "synthetic-next"), "lease");
    assertThat(call(EXEC_SERVICE, "complete", next, generate(task, next)).toString())
        .isEqualTo("APPLIED");
  }

  @Test
  void TC236_unregisteredOldStagingRequiresCandidateAndPermitAndBatchLimit() throws Exception {
    var task = create();
    var lease = lease(task);
    Object workspace = port(STORAGE, "prepareAttempt", context(task, lease));
    // 合法attempt工作区下制造进程崩溃遗留，不拼接外部路径。
    Path directory =
        root.resolve("tasks")
            .resolve(task.toString())
            .resolve("attempts")
            .resolve(property(lease, "fence") + "-" + property(lease, "owner"))
            .resolve("staging");
    Files.createDirectories(directory);
    List<Path> files = new ArrayList<>();
    for (int i = 0; i < 23; i++) {
      Path file = directory.resolve(UUID.randomUUID() + ".xlsx.part");
      Files.writeString(file, "synthetic orphan");
      Files.setLastModifiedTime(
          file, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofDays(2))));
      files.add(file);
    }
    int[] permits = {0};
    probe.on(
        ARTIFACT,
        "claimCleanup",
        (m, a, real) -> {
          Object permit = real.run();
          if (((Optional<?>) permit).isPresent()) permits[0]++;
          return permit;
        });
    call(CLEANUP, "scanCandidates", 20);
    assertThat(files.stream().filter(Files::exists).count()).isEqualTo(23); // 有效attempt保护。
    db.update(
        "UPDATE export_cleanup_candidates SET observed_at=UTC_TIMESTAMP()-INTERVAL 48 HOUR WHERE task_id=?",
        task.toString());
    expireExecution(task);
    db.update(
        "UPDATE task_execution SET attempt_deadline=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    call(CLEANUP, "scanCandidates", 20);
    long deleted = 23 - files.stream().filter(Files::exists).count();
    assertThat(deleted).isPositive().isLessThanOrEqualTo(20);
    assertThat(permits[0]).isEqualTo(deleted);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_cleanup_candidates WHERE task_id=? AND state='CLEANED'",
                Long.class,
                task.toString()))
        .isEqualTo(deleted);
    call(CLEANUP, "scanCandidates", 20);
    db.update(
        "UPDATE export_cleanup_candidates SET observed_at=UTC_TIMESTAMP()-INTERVAL 48 HOUR WHERE task_id=?",
        task.toString());
    call(CLEANUP, "scanCandidates", 20);
    assertThat(files).allMatch(f -> !Files.exists(f));
    call(CLEANUP, "scanCandidates", 20);
    assertThat(count("export_artifacts")).isZero();
  }

  @Test
  void TC236_youngOrphanAndUnknownDatabaseAreNeverDeleted() throws Exception {
    var task = create();
    var lease = lease(task);
    port(STORAGE, "prepareAttempt", context(task, lease));
    Path dir =
        root.resolve("tasks")
            .resolve(task.toString())
            .resolve("attempts")
            .resolve(property(lease, "fence") + "-" + property(lease, "owner"))
            .resolve("staging");
    Files.createDirectories(dir);
    Path file = dir.resolve(UUID.randomUUID() + ".xlsx.part");
    Files.writeString(file, "synthetic young orphan");
    expireExecution(task);
    call(CLEANUP, "scanCandidates", 20);
    assertThat(file).exists();
    Files.setLastModifiedTime(
        file, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofDays(2))));
    probe.on(
        ARTIFACT,
        "claimCleanup",
        (m, a, real) -> {
          throw new java.sql.SQLTransientConnectionException("synthetic DB unreadable");
        });
    try {
      call(CLEANUP, "scanCandidates", 20);
    } catch (java.lang.reflect.InvocationTargetException classified) {
      /* 清理保守停止。 */
    }
    assertThat(file).exists();
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC236_cleanupWinnerRejectsStaleTokenAndLateReadyAdoption() throws Exception {
    var task = create();
    var old = lease(task);
    var saved = new java.util.concurrent.atomic.AtomicReference<Object>();
    probe.on(
        ARTIFACT,
        "registerReady",
        (m, a, real) -> {
          saved.set(a[1]);
          return enumValue(m.getReturnType(), "UNKNOWN");
        });
    assertThat(property(generate(task, old), "kind").toString()).isEqualTo("UNKNOWN");
    probe.clear();
    Object proof = saved.get();
    assertThat(proof).isNotNull();
    expireExecution(task);
    db.update(
        "UPDATE task_execution SET attempt_deadline=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    Object ref = property(proof, "artifactRef");
    Path path = (Path) port(STORAGE, "resolve", ref);
    assertThat(path).exists();
    Object candidate =
        record(
            method(ARTIFACT, "claimCleanup").getParameterTypes()[0],
            Map.of(
                "id",
                UUID.randomUUID(),
                "ref",
                ref,
                "taskId",
                task,
                "owner",
                property(old, "owner"),
                "fence",
                property(old, "fence"),
                "observedAt",
                Instant.now().minus(Duration.ofDays(2))));
    call(
        "com.exportflow.exporttasks.infrastructure.persistence.ArtifactMapper",
        "registerCandidate",
        candidate);
    Object permit =
        ((Optional<?>) port(ARTIFACT, "claimCleanup", candidate, Instant.now())).orElseThrow();
    Object wrong = copy(permit, Map.of("token", UUID.randomUUID()));
    assertThat(call(CLEANUP, "deleteWithPermit", wrong)).isEqualTo(false);
    assertThat(path).exists();
    var next = property(claim(task, "synthetic-after-cleaning"), "lease");
    assertThat(port(ARTIFACT, "registerReady", next, proof).toString()).isNotEqualTo("APPLIED");
    assertThat(call(CLEANUP, "deleteWithPermit", permit)).isEqualTo(true);
    port(ARTIFACT, "completeCleanup", permit);
    assertThat(path).doesNotExist();
    assertThat(call(CLEANUP, "deleteWithPermit", permit)).isEqualTo(false);
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC236_crashedScratchIsProtectedWhileAttemptLivesAndCleanedOnlyAfterStopAndGrace()
      throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    call(DISPATCH, "scanAvailable");
    var worker =
        startWorker(
            List.of(
                "--test-barrier-port=" + STORAGE,
                "--test-barrier-method=readBlock",
                "--test-barrier-phase=after",
                "--test-barrier-occurrence=2",
                "--test-barrier-marker=scratch.marker"));
    try {
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(90))
          .untilAsserted(() -> assertThat(root.resolve("scratch.marker")).exists());
      List<Path> scratch;
      try (var files = Files.walk(root.resolve("scratch"))) {
        scratch = files.filter(Files::isRegularFile).toList();
      }
      assertThat(scratch).isNotEmpty();
      for (Path file : scratch)
        Files.setLastModifiedTime(
            file, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofDays(2))));
      call(CLEANUP, "scanCandidates", 20);
      assertThat(scratch).allMatch(Files::exists);
      worker.destroyForcibly();
      assertThat(worker.waitFor(20, TimeUnit.SECONDS)).isTrue();
      expireExecution(task);
      db.update(
          "UPDATE task_execution SET attempt_deadline=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE task_id=?",
          task.toString());
      db.update(
          "UPDATE export_cleanup_candidates SET observed_at=UTC_TIMESTAMP()-INTERVAL 48 HOUR WHERE task_id=?",
          task.toString());
      call(CLEANUP, "scanCandidates", 20);
      assertThat(scratch).allMatch(f -> !Files.exists(f));
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                  Long.class,
                  task.toString()))
          .isPositive();
    } finally {
      if (worker.isAlive()) stopWorker(worker);
    }
  }
}
