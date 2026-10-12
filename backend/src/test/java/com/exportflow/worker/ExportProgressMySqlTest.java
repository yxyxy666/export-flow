// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import org.junit.jupiter.api.Test;

// [AC-015](../../../../../../../docs/product/acceptance-criteria.md#ac-015)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [SC-005](../../../../../../../docs/product/acceptance-criteria.md#sc-005)
class ExportProgressMySqlTest extends ProductionBackendFixture {
  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Test
  void TC232_successHasActualCountFinishTimeAndUniqueResultInSameCommittedRecord()
      throws Exception {
    var task = create();
    awaitSuccess(task);
    var result =
        db.queryForMap(
            "SELECT total_count,processed_count,finished_at,file_name,file_size,result_artifact_id FROM export_tasks WHERE task_id=?",
            task.toString());
    assertThat(result.get("processed_count")).isEqualTo(result.get("total_count"));
    assertThat(result.get("finished_at")).isNotNull();
    assertThat(result.get("result_artifact_id")).isNotNull();
    assertThat(result.get("file_name").toString()).startsWith("订单导出_").endsWith(".xlsx");
    assertThat(((Number) result.get("file_size")).longValue()).isPositive();
    assertThat(
            db.queryForObject(
                "SELECT SUM(row_count) FROM export_data_blocks WHERE task_id=? AND data_generation=(SELECT data_generation FROM export_checkpoints WHERE task_id=?)",
                Long.class,
                task.toString(),
                task.toString()))
        .isEqualTo(((Number) result.get("processed_count")).longValue());
  }
}
