// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.TaskManagementFixture;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

// [AC-088](../../../../../../../docs/product/acceptance-criteria.md#ac-088)
// [AC-089](../../../../../../../docs/product/acceptance-criteria.md#ac-089)
// [AC-090](../../../../../../../docs/product/acceptance-criteria.md#ac-090)
class AssemblyRepairMySqlTest extends TaskManagementFixture {
  private Object audit(UUID task, Object lease) throws Exception {
    var snapshot = port(REPAIR, "load", task);
    return call(REPAIR_SERVICE, "audit", context(task, lease), snapshot, null, guard(true));
  }

  @Test
  void TC415_repairBudgetAndEpochSurviveProcessRestartWithoutNewCommand() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    port(REPAIR, "beginRepair", lease, audit(task, lease));
    var before =
        db.queryForMap(
            "SELECT repair_attempt,repair_epoch FROM export_tasks WHERE task_id=?",
            task.toString());
    assertThat(((Number) before.get("repair_attempt")).intValue()).isEqualTo(1);
    restartApplication();
    assertThat(
            db.queryForMap(
                "SELECT repair_attempt,repair_epoch FROM export_tasks WHERE task_id=?",
                task.toString()))
        .isEqualTo(before);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM command_outbox WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(1);
    assertThat(port(REPAIR, "load", task)).isNotNull();
    var replay = port(REPAIR, "beginRepair", lease, audit(task, lease));
    assertThat(property(replay, "kind").toString()).isEqualTo("REPLAY");
    assertThat(
            db.queryForMap(
                "SELECT repair_attempt,repair_epoch FROM export_tasks WHERE task_id=?",
                task.toString()))
        .isEqualTo(before);
  }

  @Test
  void TC415_replacementCommitResponseLostFindsSameBlockAndKeepsAllHealthyData() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    var committed = new java.util.concurrent.atomic.AtomicBoolean();
    probe.on(
        REPAIR,
        "commitReplacement",
        (method, args, real) -> {
          var result = real.run();
          if (committed.compareAndSet(false, true))
            throw new IllegalStateException("synthetic committed response lost");
          return result;
        });
    var result = generate(task, lease);
    assertThat(committed).isTrue();
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(
            db.queryForObject(
                "SELECT trusted_processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(6);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(4);
    assertThat(probe.calls(REPAIR, "findReplacement")).isEqualTo(1);
  }

  @Test
  void TC414_legacyUnsealedBlocksAreActuallyReadAndSealedWithoutQueryingSourceAgain()
      throws Exception {
    var task = create();
    var lease = lease(task);
    var data = collect(task, lease);
    db.update("DELETE FROM export_data_segments WHERE task_id=?", task.toString());
    db.update(
        "UPDATE export_checkpoints SET manifest_sealed=FALSE,segment_count=0,manifest_row_count=0,manifest_digest=NULL WHERE task_id=?",
        task.toString());
    db.update("UPDATE export_tasks SET trusted_processed_count=0 WHERE task_id=?", task.toString());
    int reads = probe.calls(ORDERS, "readBatch");
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("READY");
    assertThat(probe.calls(ORDERS, "readBatch")).isEqualTo(reads);
    assertThat(
            db.queryForObject(
                "SELECT manifest_sealed FROM export_checkpoints WHERE task_id=?",
                Boolean.class,
                task.toString()))
        .isTrue();
    assertThat(
            db.queryForObject(
                "SELECT active_block_id FROM export_data_segments WHERE task_id=?",
                String.class,
                task.toString()))
        .isEqualTo(data.blocks().getFirst().blockId().toString());
  }

  @Test
  void TC415_readyRegistrationAndAdoptionRequireSealedAndFullyTrustedData() throws Exception {
    var task = create();
    var lease = lease(task);
    var result = generate(task, lease);
    var proof = property(result, "artifact");
    db.update("UPDATE export_tasks SET trusted_processed_count=0 WHERE task_id=?", task.toString());
    assertThat(port(ARTIFACT, "registerReady", lease, proof).toString()).isEqualTo("STALE");
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("STALE");
    db.update(
        "UPDATE export_tasks SET trusted_processed_count=total_count WHERE task_id=?",
        task.toString());
    db.update(
        "UPDATE export_checkpoints SET manifest_sealed=FALSE WHERE task_id=?", task.toString());
    assertThat(port(ARTIFACT, "registerReady", lease, proof).toString()).isEqualTo("STALE");
    assertThat(port(EXECUTION, "complete", lease, result).toString()).isEqualTo("STALE");
  }

  @Test
  void TC416_legacyGenerationResetClearsTrustedProgressAndRecordsNotification() throws Exception {
    var task = create();
    var lease = lease(task);
    collect(task, lease);
    var checkpoint = port(CHECKPOINT, "find", task);
    db.update(
        "UPDATE export_checkpoints SET manifest_sealed=FALSE WHERE task_id=?", task.toString());
    var prefix =
        new com.exportflow.exporttasks.domain.ExportFileModels.TrustedPrefix(
            ((Number) property(checkpoint, "generation")).longValue(),
            ((Number) property(checkpoint, "version")).longValue(),
            List.of(),
            null,
            0,
            true);
    var result =
        port(CHECKPOINT, "startGeneration", lease, property(checkpoint, "version"), prefix);
    assertThat(property(result, "kind").toString()).isEqualTo("APPLIED");
    var summary = listedTask(task);
    assertThat(summary.path("processedCount").asLong()).isZero();
    assertThat(
            db.queryForObject(
                "SELECT pending_version FROM task_progress_outbox WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(summary.path("version").asLong());
  }

  @Test
  void TC416_auditCommitsTrustedRowsAndNotificationBeforeAnyReplacement() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    var audit = audit(task, lease);
    var started = port(REPAIR, "beginRepair", lease, audit);
    assertThat(property(started, "kind").toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForObject(
                "SELECT trusted_processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(4);
    assertThat(
            db.queryForObject(
                "SELECT processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(6);
    assertThat(
            db.queryForObject(
                "SELECT repair_state FROM export_tasks WHERE task_id=?",
                String.class,
                task.toString()))
        .isEqualTo("REBUILDING");
    var row = listedTask(task);
    assertThat(row.path("processedCount").asLong()).isEqualTo(4);
    assertThat(row.path("progressPercent").asInt()).isEqualTo(66);
    assertThat(
            db.queryForObject(
                "SELECT pending_version FROM task_progress_outbox WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(row.path("version").asLong());
  }

  @Test
  void TC415_replacementReplayCannotIncreaseTrustedCountTwiceAndOldAuditIsStale() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    var audit = audit(task, lease);
    var started = port(REPAIR, "beginRepair", lease, audit);
    var plan = property(started, "plan");
    var segment = ((List<?>) property(plan, "damagedSegments")).get(0);
    var workspace = port(STORAGE, "prepareAttempt", context(task, lease));
    var candidate =
        call(
            "com.exportflow.worker.application.ExportDataCollectionService",
            "rebuildSegment",
            context(task, lease),
            segment,
            workspace,
            guard(true));
    assertThat(
            property(port(REPAIR, "commitReplacement", lease, plan, segment, candidate), "kind")
                .toString())
        .isEqualTo("APPLIED");
    assertThat(
            property(port(REPAIR, "commitReplacement", lease, plan, segment, candidate), "kind")
                .toString())
        .isEqualTo("REPLAY");
    assertThat(
            db.queryForObject(
                "SELECT trusted_processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(6);
    assertThat(property(port(REPAIR, "beginRepair", lease, audit), "kind").toString())
        .isEqualTo("STALE");
  }

  @Test
  void TC415_pauseBarrierRejectsReplacementAndKeepsOriginalProtectedBlocks() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    Files.delete((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef()));
    var started = port(REPAIR, "beginRepair", lease, audit(task, lease));
    var plan = property(started, "plan");
    var segment = ((List<?>) property(plan, "damagedSegments")).get(0);
    var workspace = port(STORAGE, "prepareAttempt", context(task, lease));
    var candidate =
        call(
            "com.exportflow.worker.application.ExportDataCollectionService",
            "rebuildSegment",
            context(task, lease),
            segment,
            workspace,
            guard(true));
    assertThat(pause(task, UUID.randomUUID(), "0").statusCode()).isEqualTo(200);
    assertThat(
            property(port(REPAIR, "commitReplacement", lease, plan, segment, candidate), "kind")
                .toString())
        .isEqualTo("STALE");
    assertThat(state(task)).isEqualTo("PAUSING");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(3);
    assertThat((Path) port(STORAGE, "resolve", data.blocks().get(2).storageRef())).exists();
  }

  @Test
  void TC415_missingManifestCannotAdoptOrphanFileOrGuessOriginalRange() throws Exception {
    useThreeSmallBlocks();
    var task = createVolume();
    var lease = lease(task);
    var data = collect(task, lease);
    db.update("DELETE FROM export_data_segments WHERE task_id=? AND sequence=2", task.toString());
    db.update(
        "DELETE FROM export_data_blocks WHERE block_id=?",
        data.blocks().get(1).blockId().toString());
    var result = generate(task, lease);
    assertThat(property(result, "kind").toString()).isEqualTo("KNOWN_FAILURE");
    assertThat(property(property(result, "error"), "code").toString())
        .isEqualTo("DATA_REPAIR_UNRECOVERABLE");
    assertThat((Path) port(STORAGE, "resolve", data.blocks().get(1).storageRef())).exists();
  }
}
