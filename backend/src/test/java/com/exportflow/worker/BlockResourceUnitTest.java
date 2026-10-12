// [AC-069](../../../../../../../docs/product/acceptance-criteria.md#ac-069)
// 规格：[块读取](../../../../../../../specs/002-order-management/excel-design.md)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.worker.infrastructure.file.DataBlockCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BlockResourceUnitTest {
  @Test
  void TC235_invalidBlockKeepsPrimaryDamageEvidenceEvenWhenCloseFails() {
    var closed = new AtomicBoolean();
    var cleanup = new IOException("synthetic close failure");
    var input =
        new ByteArrayInputStream(new byte[0]) {
          @Override
          public void close() throws IOException {
            closed.set(true);
            throw cleanup;
          }
        };
    var codec = new DataBlockCodec(new ObjectMapper());
    var ref = new BlockRef(UUID.randomUUID(), null, null, 0, "synthetic");
    var failure = catchThrowable(() -> codec.open(ref, input));
    assertThat(closed).isTrue();
    assertThat(failure).isInstanceOf(InvalidExportDataException.class);
    assertThat(failure.getSuppressed()).containsExactly(cleanup);
  }
}
