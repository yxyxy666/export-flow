// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.infrastructure.file;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.worker.domain.AssemblyRepairModels.*;
import com.exportflow.worker.port.ExecutionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 每行1MiB、每块16MiB预算，EOF核验SHA与数量；设计 excel-design.md，TC-235。 */
@Component
public class DataBlockCodec {
  private final ObjectMapper json;

  public DataBlockCodec(ObjectMapper json) {
    this.json = json;
  }

  /** 原始字段固定顺序及长度边界编码，不含块ID或header。 */
  public String checksumRows(List<Order> rows, int formatVersion) {
    if (formatVersion != 1 || rows.isEmpty() || rows.size() > 1000)
      throw new InvalidExportDataException();
    var hash = digest();
    for (var row : rows)
      for (var value :
          Arrays.asList(
              Long.toString(row.id()),
              row.orderNo(),
              row.customerName(),
              row.customerPhone(),
              row.status(),
              row.channel(),
              row.amount() == null ? null : row.amount().stripTrailingZeros().toPlainString(),
              row.currency(),
              row.region(),
              row.createdAt() == null ? null : row.createdAt().toString())) {
        var bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) hash.update(bytes);
      }
    return HexFormat.of().formatHex(hash.digest());
  }

  /** 读到EOF，同时校验行序、完整header、文件摘要和独立内容基线。调用方负责关闭reader。 */
  public VerifiedBlock verifyContent(
      BlockReader reader, BlockRef block, SegmentBaseline expected, ExecutionGuard guard)
      throws IOException {
    List<Order> rows = new ArrayList<>();
    var cursor = block.header().previousCursor();
    for (var next = reader.next(); next.isPresent(); next = reader.next()) {
      if (!guard.check()) throw new ExecutionStoppedException();
      var row = next.get();
      var actual = new OrderCursor(row.createdAt(), row.id());
      if (cursor != null
          && (actual.createdAt().isAfter(cursor.createdAt())
              || actual.createdAt().equals(cursor.createdAt()) && actual.id() >= cursor.id()))
        throw new InvalidExportDataException();
      rows.add(row);
      cursor = actual;
      if (rows.size() > 1000) throw new InvalidExportDataException();
    }
    var header = block.header();
    if (rows.size() != header.rowCount() || !Objects.equals(cursor, header.lastCursor()))
      throw new InvalidExportDataException();
    var checksum = checksumRows(rows, header.version());
    var baseline =
        new SegmentBaseline(
            header.sequence(),
            header.previousCursor(),
            header.lastCursor(),
            header.rowCount(),
            header.configHash(),
            header.version(),
            checksum);
    if (expected != null && !expected.equals(baseline)) throw new InvalidExportDataException();
    return new VerifiedBlock(block, checksum);
  }

  public BlockDigest write(BlockHeader header, List<Order> rows, OutputStream output)
      throws IOException {
    if (header.rowCount() != rows.size() || rows.isEmpty() || rows.size() > 1000)
      throw new InvalidExportDataException();
    MessageDigest hash = digest();
    long size = 0;
    byte[] metadata = json.writeValueAsBytes(header);
    size = writeLine(metadata, output, hash, size);
    for (var row : rows) size = writeLine(json.writeValueAsBytes(row), output, hash, size);
    output.flush();
    return new BlockDigest(size, rows.size(), HexFormat.of().formatHex(hash.digest()));
  }

  private long writeLine(byte[] line, OutputStream output, MessageDigest hash, long previous)
      throws IOException {
    long next = previous + line.length + 1;
    if (line.length > 1048576 || next > 16777216) throw new InvalidExportDataException();
    output.write(line);
    output.write('\n');
    hash.update(line);
    hash.update((byte) '\n');
    return next;
  }

  private MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("摘要能力不可用");
    }
  }

  public BlockReader open(BlockRef ref, InputStream input) throws IOException {
    var reader = new Reader(ref, input);
    try {
      reader.readHeader();
      return reader;
    } catch (IOException | RuntimeException | Error failure) {
      try {
        input.close();
      } catch (IOException | RuntimeException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  private final class Reader implements BlockReader {
    private final BlockRef ref;
    private final InputStream input;
    private final MessageDigest hash = digest();
    private long bytes;
    private int rows;
    private boolean ended;

    Reader(BlockRef ref, InputStream input) {
      this.ref = ref;
      this.input = input;
    }

    private byte[] line() throws IOException {
      var buffer = new ByteArrayOutputStream();
      int value;
      while ((value = input.read()) != -1) {
        bytes++;
        if (bytes > 16777216) throw new InvalidExportDataException();
        hash.update((byte) value);
        if (value == '\n') return buffer.toByteArray();
        if (buffer.size() >= 1048576) throw new InvalidExportDataException();
        buffer.write(value);
      }
      if (buffer.size() != 0) throw new InvalidExportDataException();
      return null;
    }

    void readHeader() throws IOException {
      byte[] line = line();
      if (line == null) throw new InvalidExportDataException();
      BlockHeader header;
      try {
        header = json.readValue(line, BlockHeader.class);
      } catch (com.fasterxml.jackson.core.JsonProcessingException bad) {
        throw new InvalidExportDataException();
      }
      if (!header.equals(ref.header()) || header.version() != 1)
        throw new InvalidExportDataException();
    }

    public Optional<Order> next() throws IOException {
      if (ended) return Optional.empty();
      byte[] line = line();
      if (line == null) {
        ended = true;
        if (bytes != ref.byteSize()
            || rows != ref.header().rowCount()
            || !HexFormat.of().formatHex(hash.digest()).equals(ref.checksum()))
          throw new InvalidExportDataException();
        return Optional.empty();
      }
      if (++rows > ref.header().rowCount()) throw new InvalidExportDataException();
      try {
        return Optional.of(json.readValue(line, Order.class));
      } catch (com.fasterxml.jackson.core.JsonProcessingException bad) {
        throw new InvalidExportDataException();
      }
    }

    public void close() throws IOException {
      input.close();
    }
  }
}
