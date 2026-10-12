// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.ProductionBackendFixture;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

// [AC-049](../../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-053](../../../../../../../docs/product/acceptance-criteria.md#ac-053)
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
class ExecutionLeaseMySqlTest extends ProductionBackendFixture {
  private static final String SERVICE = "com.exportflow.worker.application.TaskExecutionService";

  @Test
  void TC224_expiredLeaseCannotRenewItselfOrTakeOverBeforePersistentBackoff() throws Exception {
    var task = create();
    var acquired = call(SERVICE, "claim", task, "synthetic-owner-a");
    assertThat(component(acquired, "kind").toString()).isEqualTo("ACQUIRED");
    long fence =
        db.queryForObject(
            "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString());
    db.update(
        "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE task_id=?",
        task.toString());
    assertThat(call(SERVICE, "renew", component(acquired, "lease")).toString()).isEqualTo("STALE");
    var busy = call(SERVICE, "claim", task, "synthetic-owner-b");
    assertThat(component(busy, "kind").toString()).isEqualTo("BUSY");
    assertThat(
            db.queryForObject(
                "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString()))
        .isEqualTo(fence);
    assertThat(
            db.queryForObject(
                "SELECT recovery_count FROM task_execution WHERE task_id=?",
                Integer.class,
                task.toString()))
        .isZero();
    assertThat(state(task)).isEqualTo("RUNNING");
  }

  @Test
  void TC224_concurrentClaimsGrantOnlyOneExecutionAndExpiredTakeoverIncrementsFence()
      throws Exception {
    var task = create();
    var claims = new ArrayList<Object>();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var barrier = new CyclicBarrier(2);
      var first =
          threads.submit(
              () -> {
                barrier.await();
                return call(SERVICE, "claim", task, "synthetic-owner-a");
              });
      var second =
          threads.submit(
              () -> {
                barrier.await();
                return call(SERVICE, "claim", task, "synthetic-owner-b");
              });
      claims.add(first.get(10, TimeUnit.SECONDS));
      claims.add(second.get(10, TimeUnit.SECONDS));
    }
    assertThat(
            claims.stream().filter(c -> component(c, "kind").toString().equals("ACQUIRED")).count())
        .isEqualTo(1);
    assertThat(state(task)).isEqualTo("RUNNING");
    long fence =
        db.queryForObject(
            "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString());
    db.update(
        "UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",
        task.toString());
    var takeover = call(SERVICE, "claim", task, "synthetic-owner-c");
    assertThat(component(takeover, "kind").toString()).isEqualTo("ACQUIRED");
    assertThat(
            db.queryForObject(
                "SELECT fence FROM task_execution WHERE task_id=?", Long.class, task.toString()))
        .isGreaterThan(fence);
    var old =
        claims.stream()
            .filter(c -> component(c, "kind").toString().equals("ACQUIRED"))
            .findFirst()
            .orElseThrow();
    assertThat(call(SERVICE, "renew", component(old, "lease")).toString()).isEqualTo("STALE");
  }

  private Object component(Object object, String method) {
    try {
      var accessor = object.getClass().getDeclaredMethod(method);
      accessor.trySetAccessible();
      return accessor.invoke(object);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }
}
