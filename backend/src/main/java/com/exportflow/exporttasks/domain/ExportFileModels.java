// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[DownloadBoundaryTest.java](../../../../../../test/java/com/exportflow/worker/DownloadBoundaryTest.java)、[DownloadExportFileTest.java](../../../../../../test/java/com/exportflow/worker/DownloadExportFileTest.java)。
package com.exportflow.exporttasks.domain;

import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** 文件、块及校验值；设计 data-model.md，TC-234/235/236。 */
public final class ExportFileModels {
  private ExportFileModels() {}

  public record StorageRef(
      String kind, UUID taskId, UUID identity, Long generation, Long attemptFence, String owner) {}

  public record AttemptWorkspace(UUID taskId, String owner, long fence, String namespace) {}

  public record ArtifactProof(
      UUID artifactId,
      UUID taskId,
      long dataGeneration,
      String configurationHash,
      StorageRef artifactRef,
      String fileName,
      long size,
      long rowCount,
      String checksum) {}

  public record SafeFailure(String code, String message) {
    public SafeFailure {
      if (code == null
          || !code.matches("[A-Z0-9_]{1,64}")
          || message == null
          || message.length() > 256) throw new IllegalArgumentException("失败记录无效");
    }
  }

  /** 仅在明确停止检查抛出，关闭异常通过suppressed保留，不能伪造已关闭。 */
  public static class ExecutionStoppedException extends IOException {
    public ExecutionStoppedException() {
      super("执行已停止");
    }
  }

  public record BlockHeader(
      int version,
      String configHash,
      long generation,
      int sequence,
      OrderCursor previousCursor,
      OrderCursor lastCursor,
      int rowCount) {}

  public record BlockRef(
      UUID blockId, BlockHeader header, StorageRef storageRef, long byteSize, String checksum) {}

  public record BlockDigest(long bytes, int rows, String checksum) {}

  public interface BlockReader extends AutoCloseable {
    Optional<Order> next() throws IOException;

    @Override
    void close() throws IOException;
  }

  public record CheckpointSnapshot(
      UUID taskId,
      long generation,
      long version,
      OrderCursor cursor,
      long verifiedPrefixCount,
      long processedHighWater,
      String configurationHash,
      List<BlockRef> blocks) {
    public CheckpointSnapshot {
      blocks = List.copyOf(blocks);
    }
  }

  public record TrustedPrefix(
      long generation,
      long version,
      List<BlockRef> blocks,
      OrderCursor cursor,
      long verifiedCount,
      boolean isSafeToRedo) {
    public TrustedPrefix {
      blocks = List.copyOf(blocks);
    }
  }

  public record CompletedDataset(
      UUID taskId, long generation, String configHash, List<BlockRef> blocks, long totalRows) {
    public CompletedDataset {
      blocks = List.copyOf(blocks);
    }
  }

  public enum CheckpointWriteKind {
    APPLIED,
    STALE,
    UNKNOWN
  }

  public record CheckpointWriteResult(CheckpointWriteKind kind, CheckpointSnapshot snapshot) {}

  public record StagingWorkbook(StorageRef ref, UUID artifactId, long rows, String fileName) {}

  public record ValidatedWorkbook(
      StorageRef ref, long size, String checksum, long rowCount, String configHash) {}

  public record StorageCandidate(
      UUID id, StorageRef ref, UUID taskId, String owner, Long fence, Instant observedAt) {}

  public record CleanupPermit(UUID candidateId, UUID token, StorageRef ref) {}

  public record DownloadMetadata(String fileName, String mimeType, long length) {}

  public static final class DownloadHandle implements AutoCloseable {
    private final DownloadMetadata metadata;
    private final InputStream input;
    private final java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();

    public DownloadHandle(DownloadMetadata metadata, InputStream input) {
      this.metadata = metadata;
      this.input = input;
    }

    public DownloadMetadata metadata() {
      return metadata;
    }

    public InputStream input() {
      return input;
    }

    public void writeTo(OutputStream output) throws IOException {
      try (this) {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      }
    }

    @Override
    public void close() throws IOException {
      if (closed.compareAndSet(false, true)) input.close();
    }
  }

  /** 已证明格式损坏或业务数据不可表达；I/O故障保留为UNKNOWN。 */
  public static class InvalidExportDataException extends RuntimeException {
    private final String code;

    public InvalidExportDataException() {
      this("EXPORT_CHECKPOINT_INVALID");
    }

    public InvalidExportDataException(String code) {
      super("导出数据或产物校验失败");
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}
