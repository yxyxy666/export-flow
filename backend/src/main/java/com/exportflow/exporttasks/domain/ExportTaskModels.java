// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
package com.exportflow.exporttasks.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 创建/幂等内部值类型；设计 data-model.md，验证 TC-217/218。 */
public final class ExportTaskModels {
  private ExportTaskModels() {}

  public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  public record CreateExportCommand(ExportScope scope, List<ExportField> fields, String key) {
    public CreateExportCommand {
      fields = List.copyOf(fields);
    }
  }

  public record CanonicalSubmission(
      int version,
      byte[] requestHash,
      byte[] keyHash,
      ExportScope scope,
      List<ExportField> fields) {
    public CanonicalSubmission {
      requestHash = requestHash.clone();
      keyHash = keyHash.clone();
      fields = List.copyOf(fields);
    }

    @Override
    public byte[] requestHash() {
      return requestHash.clone();
    }

    @Override
    public byte[] keyHash() {
      return keyHash.clone();
    }
  }

  public record IdempotencyRecord(int canonicalVersion, byte[] requestHash, UUID taskId) {
    public IdempotencyRecord {
      requestHash = requestHash.clone();
    }

    @Override
    public byte[] requestHash() {
      return requestHash.clone();
    }
  }

  public record TaskIdentity(UUID taskId, UUID eventId, String taskNo) {}

  public record TaskSummary(
      UUID taskId,
      String taskNo,
      String scopeType,
      long totalCount,
      List<ExportField> fields,
      TaskStatus status,
      long processedCount,
      int progressPercent,
      Instant createdAt,
      Instant finishedAt,
      String errorCode,
      String errorSummary,
      String fileName,
      Long fileSize) {
    public TaskSummary {
      fields = List.copyOf(fields);
    }
  }

  public record TaskSnapshot(ExportTask task, ExportScope scope, List<ExportField> fields) {
    public TaskSnapshot {
      fields = List.copyOf(fields);
    }

    /** 只使用任务权威受理时间与编号生成展示文件名。 */
    public String fileName() {
      if (!task.taskNo().matches("[A-Z0-9_-]{1,64}"))
        throw new ExportFileModels.InvalidExportDataException("EXPORT_FILE_INVALID");
      String stamp =
          java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
              .withZone(java.time.ZoneId.of("Asia/Hong_Kong"))
              .format(task.createdAt());
      return "订单导出_" + stamp + "_" + task.taskNo() + ".xlsx";
    }

    /** 长度前缀编码固定范围与有序字段，跨尝试复用同一配置身份。 */
    public String configurationHash() {
      try {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var values = new java.util.ArrayList<String>();
        values.add(scope.excludedOrderIds().isEmpty() ? "configuration-v1" : "configuration-v2");
        values.add(scope.type());
        if (scope.orderIds() != null)
          values.add(scope.orderIds().stream().sorted().toList().toString());
        else {
          var f = scope.filter();
          for (Object value :
              new Object[] {
                f.keyword(),
                f.orderNo(),
                f.status(),
                f.channel(),
                f.createdFrom(),
                f.createdTo(),
                f.minAmount(),
                f.maxAmount()
              }) values.add(value == null ? null : value.toString());
        }
        if (!scope.excludedOrderIds().isEmpty())
          values.add(scope.excludedOrderIds().stream().distinct().sorted().toList().toString());
        values.add(fields.toString());
        for (String value : values) {
          byte[] bytes =
              value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          digest.update(
              java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
          digest.update(bytes);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
      } catch (java.security.NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("摘要能力不可用");
      }
    }
  }

  public record CreateTaskResult(TaskSummary task, boolean isReplay) {}

  public record ExportRangeCheck(long total, ExportScope scope) {}
}
