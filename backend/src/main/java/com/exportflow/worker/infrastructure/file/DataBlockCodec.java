// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[BlockResourceUnitTest.java](../../../../../../../test/java/com/exportflow/worker/BlockResourceUnitTest.java)、[CheckpointInvariantTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointInvariantTest.java)、[CheckpointRecoveryTest.java](../../../../../../../test/java/com/exportflow/worker/CheckpointRecoveryTest.java)。
package com.exportflow.worker.infrastructure.file;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.orders.domain.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
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
