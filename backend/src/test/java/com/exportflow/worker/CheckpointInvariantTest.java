// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
// 规格：[断点模型](../../../../../../../specs/002-order-management/data-model.md)
package com.exportflow.worker;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.exportflow.testing.BoundaryFixture;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CheckpointInvariantTest extends BoundaryFixture {
  @Test
  void TC235_expectedCountMustMatchLockedDatabasePrefixBeforeAnyAdvance() throws Exception {
    useOrderVolume(2501);
    var task = createVolume();
    var lease = lease(task);
    var expected = new AtomicReference<Object>();
    var block = new AtomicReference<Object>();
    probe.on(
        CHECKPOINT,
        "commitBlock",
        (method, args, real) -> {
          expected.compareAndSet(null, args[1]);
          block.compareAndSet(null, args[2]);
          throw new java.sql.SQLTransientConnectionException("synthetic pre-commit interruption");
        });
    assertThat(property(generate(task, lease), "kind").toString()).isEqualTo("UNKNOWN");
    probe.clear();
    assertThat(block.get()).isNotNull();
    var forged = copy(expected.get(), Map.of("verifiedPrefixCount", 1L));
    var rejected = port(CHECKPOINT, "commitBlock", lease, forged, block.get());
    assertThat(property(rejected, "kind").toString()).isEqualTo("STALE");
    assertThat(
            db.queryForObject(
                "SELECT processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM export_data_blocks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isZero();
    var applied = port(CHECKPOINT, "commitBlock", lease, expected.get(), block.get());
    assertThat(property(applied, "kind").toString()).isEqualTo("APPLIED");
    assertThat(
            db.queryForObject(
                "SELECT processed_count FROM export_tasks WHERE task_id=?",
                Long.class,
                task.toString()))
        .isEqualTo(1000L);
  }
}
