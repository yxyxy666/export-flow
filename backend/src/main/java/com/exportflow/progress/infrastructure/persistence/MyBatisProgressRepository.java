// 设计：[可靠进度事务](../../../../../../../../../specs/004-task-management/function-design.md)。
package com.exportflow.progress.infrastructure.persistence;

import com.exportflow.progress.domain.ProgressModels.*;
import com.exportflow.progress.port.ProgressRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class MyBatisProgressRepository implements ProgressRepository {
  private final ProgressMapper mapper;
  private final TransactionTemplate transaction;

  public MyBatisProgressRepository(ProgressMapper mapper, PlatformTransactionManager manager) {
    this.mapper = mapper;
    transaction = new TransactionTemplate(manager);
  }

  public void lockActivity() {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("活动锁必须属于业务事务");
    mapper.lockActivity();
  }

  public void recordChange(UUID id, long version) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("通知必须属于业务事务");
    mapper.advanceActivity();
    mapper.record(id.toString(), version, mapper.activity());
  }

  public List<NoticeLease> claimPending(String owner, int limit) {
    if (owner == null || owner.isBlank() || owner.length() > 128 || limit < 1 || limit > 100)
      throw new IllegalArgumentException("通知领取无效");
    List<NoticeLease> result = new ArrayList<>();
    for (var id : mapper.due(limit)) {
      NoticeLease lease =
          transaction.execute(
              status -> {
                mapper.lock(id);
                String token = UUID.randomUUID().toString();
                if (mapper.claim(id, owner, token) != 1) return null;
                var row = mapper.lock(id);
                return new NoticeLease(
                    UUID.fromString(id),
                    row.pendingVersion(),
                    row.activityVersion(),
                    owner,
                    UUID.fromString(token));
              });
      if (lease != null) result.add(lease);
    }
    return List.copyOf(result);
  }

  public boolean markPublished(NoticeLease lease, PublishProof proof) {
    return proof != null
        && proof.reliable()
        && mapper.published(
                lease.taskId().toString(),
                lease.token().toString(),
                lease.owner(),
                lease.pendingVersion())
            == 1;
  }

  public void release(NoticeLease lease) {
    mapper.release(lease.taskId().toString(), lease.token().toString());
  }

  public IdleCandidate prepareIdle() {
    var row = mapper.idle();
    return new IdleCandidate(row.activityVersion(), row.hasActiveTasks());
  }

  public boolean confirmIdle(IdleCandidate candidate) {
    return Boolean.TRUE.equals(
        transaction.execute(
            status -> {
              lockActivity();
              var current = prepareIdle();
              return !current.hasActiveTasks()
                  && current.activityVersion() == candidate.activityVersion();
            }));
  }
}
