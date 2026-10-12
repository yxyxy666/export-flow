// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-018](../../../../../../../docs/product/acceptance-criteria.md#ac-018)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
// [AC-071](../../../../../../../docs/product/acceptance-criteria.md#ac-071)
class ArtifactCleanupRaceTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Test
  void TC236_successAndCheckpointReferencesSurviveCleanupEvenAfterGrace() throws Exception {
    var task = create();
    awaitSuccess(task);
    byte[] before = download(task);
    db.update(
        "UPDATE export_artifacts SET ready_at=UTC_TIMESTAMP()-INTERVAL 48 HOUR WHERE task_id=?",
        task.toString());
    call("com.exportflow.worker.application.OrphanCleanupService", "scanCandidates", 20);
    assertThat(download(task)).isEqualTo(before);
    assertThat(state(task)).isEqualTo("SUCCEEDED");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE task_id=? AND state='ADOPTED'",
                Long.class,
                task.toString()))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isPositive();
  }
}
