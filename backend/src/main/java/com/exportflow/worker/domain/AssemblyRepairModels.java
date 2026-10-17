// 设计：[分段修复模型](../../../../../../../../specs/004-task-management/data-model.md#选择性重建模型)。
package com.exportflow.worker.domain;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class AssemblyRepairModels {
  private AssemblyRepairModels() {}

  public record SegmentBaseline(
      int sequence,
      OrderCursor previousCursor,
      OrderCursor lastCursor,
      int rowCount,
      String configurationHash,
      int formatVersion,
      String contentChecksum) {}

  public record SegmentRef(
      int sequence,
      OrderCursor previousCursor,
      OrderCursor lastCursor,
      int rowCount,
      String configurationHash,
      int formatVersion,
      String contentChecksum,
      BlockRef selectedBlock,
      long selectionVersion,
      String state) {
    public SegmentBaseline baseline() {
      return new SegmentBaseline(
          sequence,
          previousCursor,
          lastCursor,
          rowCount,
          configurationHash,
          formatVersion,
          contentChecksum);
    }
  }

  public record RepairSnapshot(
      UUID taskId,
      long dataGeneration,
      long checkpointVersion,
      long manifestVersion,
      long repairEpoch,
      boolean manifestSealed,
      int segmentCount,
      long manifestRowCount,
      String manifestDigest,
      String configurationHash,
      long totalCount,
      int repairAttempt,
      List<SegmentRef> segments) {
    public RepairSnapshot {
      segments = List.copyOf(segments);
    }
  }

  public record VerifiedBlock(BlockRef ref, String contentChecksum) {}

  public record RepairIssue(String code, Integer sequence) {}

  public record AssemblyAudit(
      RepairSnapshot snapshot,
      RepairIssue triggerIssue,
      List<SegmentRef> healthySegments,
      List<SegmentRef> damagedSegments,
      long trustedRows,
      String fatalReason) {
    public AssemblyAudit {
      healthySegments = List.copyOf(healthySegments);
      damagedSegments = List.copyOf(damagedSegments);
    }
  }

  public record RepairPlan(
      UUID taskId,
      long dataGeneration,
      long checkpointVersion,
      long manifestVersion,
      long epoch,
      int attemptNo,
      List<SegmentRef> damagedSegments) {
    public RepairPlan {
      damagedSegments = List.copyOf(damagedSegments);
    }
  }

  public enum RepairWriteKind {
    APPLIED,
    REPLAY,
    STALE,
    UNKNOWN,
    EXHAUSTED,
    FATAL
  }

  public record RepairWriteResult(RepairWriteKind kind, RepairPlan plan) {}

  /** 长度前缀固定版本编码，只覆盖不可变基线，不包括块选择和repairEpoch。 */
  public static String manifestDigest(List<SegmentBaseline> segments) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var segment : segments)
        for (var value :
            List.of(
                Integer.toString(segment.sequence()),
                segment.previousCursor() == null
                    ? ""
                    : segment.previousCursor().createdAt() + "/" + segment.previousCursor().id(),
                segment.lastCursor().createdAt() + "/" + segment.lastCursor().id(),
                Integer.toString(segment.rowCount()),
                segment.configurationHash(),
                Integer.toString(segment.formatVersion()),
                segment.contentChecksum())) {
          var bytes = value.getBytes(StandardCharsets.UTF_8);
          digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
          digest.update(bytes);
        }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("摘要不可用");
    }
  }
}
