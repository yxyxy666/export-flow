// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[DownloadBoundaryTest.java](../../../../../../../test/java/com/exportflow/worker/DownloadBoundaryTest.java)、[DownloadExportFileTest.java](../../../../../../../test/java/com/exportflow/worker/DownloadExportFileTest.java)。
package com.exportflow.exporttasks.infrastructure.file;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.orders.domain.Order;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import com.exportflow.worker.infrastructure.file.DataBlockCodec;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.springframework.stereotype.Component;

/** 服务端身份限定路径、拒绝链接/reparse，force后原子改名；设计 excel-design.md，TC-234/236。 */
@Component
public class LocalExportFileStorage implements ExportFileStorage {
  private final Path root;
  private final DataBlockCodec codec;
  private final boolean available;

  public LocalExportFileStorage(RuntimeProperties properties, DataBlockCodec codec) {
    root = properties.getFileRoot().toAbsolutePath().normalize();
    this.codec = codec;
    boolean verified = false;
    try {
      checkPath(root);
      Files.createDirectories(root);
      checkPath(root);
      Files.createDirectories(root.resolve("scratch"));
      Path scratch = root.resolve("scratch").resolve(UUID.randomUUID().toString());
      Files.createDirectories(scratch);
      checkPath(scratch);
      org.apache.poi.util.TempFile.setTempFileCreationStrategy(
          new com.exportflow.worker.infrastructure.file.ScratchFileStrategy(scratch));
      Path source = Files.createTempFile(root, "capability-", ".part"),
          target = root.resolve(UUID.randomUUID() + ".probe");
      try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        verified = true;
      } finally {
        Files.deleteIfExists(source);
        Files.deleteIfExists(target);
      }
    } catch (IOException | RuntimeException unavailable) {
      verified = false;
    }
    available = verified;
  }

  public boolean isAvailable() {
    return available;
  }

  public Path scratchRoot() {
    return root.resolve("scratch");
  }

  public static void checkPath(Path target) throws IOException {
    Path current = target.getRoot();
    for (Path segment : target) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        BasicFileAttributes attributes =
            Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("文件路径不安全");
      }
    }
  }

  private String attempt(long fence, String owner) {
    if (fence < 1 || owner == null || !owner.matches("[A-Za-z0-9_-]{1,128}"))
      throw new InvalidExportDataException();
    return fence + "-" + owner;
  }

  public Path resolve(StorageRef ref) throws IOException {
    if (ref == null || ref.taskId() == null || ref.identity() == null)
      throw new InvalidExportDataException();
    Path task = root.resolve("tasks").resolve(ref.taskId().toString()), target;
    switch (ref.kind()) {
      case "BLOCK" -> {
        if (ref.generation() == null || ref.generation() < 1)
          throw new InvalidExportDataException();
        target =
            task.resolve("data")
                .resolve(ref.generation().toString())
                .resolve("blocks")
                .resolve(ref.identity() + ".jsonl");
      }
      case "STAGING" -> {
        if (ref.attemptFence() == null) throw new InvalidExportDataException();
        target =
            task.resolve("attempts")
                .resolve(attempt(ref.attemptFence(), ref.owner()))
                .resolve("staging")
                .resolve(ref.identity() + ".xlsx.part");
      }
      case "ARTIFACT" -> target = task.resolve("artifacts").resolve(ref.identity() + ".xlsx");
      case "BLOCK_PART" -> {
        if (ref.generation() == null || ref.generation() < 1)
          throw new InvalidExportDataException();
        target =
            task.resolve("data")
                .resolve(ref.generation().toString())
                .resolve("blocks")
                .resolve(ref.identity() + ".jsonl.part");
      }
      case "SCRATCH" -> {
        if (ref.owner() == null || ref.attemptFence() == null)
          throw new InvalidExportDataException();
        int separator = ref.owner().indexOf('_');
        if (separator < 0) throw new InvalidExportDataException();
        UUID process = UUID.fromString(ref.owner().substring(0, separator));
        target =
            root.resolve("scratch")
                .resolve(process.toString())
                .resolve(ref.taskId().toString())
                .resolve(attempt(ref.attemptFence(), ref.owner().substring(separator + 1)))
                .resolve(ref.identity() + ".tmp");
      }
      default -> throw new InvalidExportDataException();
    }
    target = target.normalize();
    if (!target.startsWith(root)) throw new InvalidExportDataException();
    checkPath(target);
    return target;
  }

  public AttemptWorkspace prepareAttempt(GenerationContext context) throws IOException {
    if (!available) throw new IOException("文件能力不可用");
    var lease = context.lease();
    var ref =
        new StorageRef(
            "STAGING", lease.taskId(), UUID.randomUUID(), null, lease.fence(), lease.owner());
    Path dir = resolve(ref).getParent();
    Files.createDirectories(dir);
    checkPath(dir);
    return new AttemptWorkspace(
        lease.taskId(), lease.owner(), lease.fence(), attempt(lease.fence(), lease.owner()));
  }

  public BlockRef writeBlock(AttemptWorkspace workspace, BlockHeader header, List<Order> rows)
      throws IOException {
    UUID identity = UUID.randomUUID();
    var ref =
        new StorageRef("BLOCK", workspace.taskId(), identity, header.generation(), null, null);
    Path target = resolve(ref);
    Files.createDirectories(target.getParent());
    checkPath(target.getParent());
    Path part = target.resolveSibling(identity + ".jsonl.part");
    BlockDigest digest;
    try (var output =
        new BufferedOutputStream(
            Files.newOutputStream(part, StandardOpenOption.CREATE_NEW), 65536)) {
      digest = codec.write(header, rows, output);
    }
    force(part);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("文件身份已存在");
    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
    return new BlockRef(identity, header, ref, digest.bytes(), digest.checksum());
  }

  public BlockReader readBlock(BlockRef ref) throws IOException {
    return codec.open(
        ref, new BufferedInputStream(Files.newInputStream(resolve(ref.storageRef())), 65536));
  }

  public ArtifactProof publish(
      AttemptWorkspace workspace, StagingWorkbook staging, ValidatedWorkbook proof)
      throws IOException {
    if (!staging.ref().equals(proof.ref())
        || !workspace.taskId().equals(staging.ref().taskId())
        || !Objects.equals(staging.ref().attemptFence(), workspace.fence())
        || !workspace.owner().equals(staging.ref().owner())
        || proof.rowCount() != staging.rows()) throw new InvalidExportDataException();
    Path part = resolve(staging.ref());
    force(part);
    var ref =
        new StorageRef("ARTIFACT", workspace.taskId(), staging.artifactId(), null, null, null);
    Path target = resolve(ref);
    Files.createDirectories(target.getParent());
    checkPath(target.getParent());
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("产物身份已存在");
    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
    return new ArtifactProof(
        staging.artifactId(),
        workspace.taskId(),
        Objects.requireNonNull(staging.ref().generation()),
        proof.configHash(),
        ref,
        staging.fileName(),
        proof.size(),
        proof.rowCount(),
        proof.checksum());
  }

  public DownloadHandle openDownload(ArtifactProof proof) throws IOException {
    Path target = resolve(proof.artifactRef());
    if (Files.size(target) != proof.size()
        || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("产物属性无效");
    return new DownloadHandle(
        new DownloadMetadata(
            proof.fileName(),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            proof.size()),
        new BufferedInputStream(Files.newInputStream(target), 65536));
  }

  private void force(Path path) throws IOException {
    try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  public void closeWorkspace(AttemptWorkspace workspace) {
    /* 文件引用永久由DB裁决；当前流由各操作结构化关闭。 */
  }
}
