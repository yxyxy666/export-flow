// 本期实施与验证：[任务记录](../../../../../../../specs/004-task-management/tasks.md)。
// [AC-068](../../../../../../../docs/product/acceptance-criteria.md#ac-068)
// 规格：[执行与断点](../../../../../../../specs/002-order-management/excel-design.md)
package com.exportflow.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.exporttasks.domain.ExportTask;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskSnapshot;
import com.exportflow.exporttasks.port.ExportFileStorage;
import com.exportflow.orders.port.OrderRepository;
import com.exportflow.worker.application.ExportDataCollectionService;
import com.exportflow.worker.domain.ExecutionModels.GenerationContext;
import com.exportflow.worker.port.CheckpointRepository;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DataCollectionGuardUnitTest {
  @Test
  void TC235_lossOfAuthorityBeforePrefixValidationDoesNotOpenAnotherBlock() throws Exception {
    var checkpoints = mock(CheckpointRepository.class);
    var orders = mock(OrderRepository.class);
    var storage = mock(ExportFileStorage.class);
    var context = mock(GenerationContext.class);
    var snapshot = mock(TaskSnapshot.class);
    var task = mock(ExportTask.class);
    var id = UUID.randomUUID();
    when(context.snapshot()).thenReturn(snapshot);
    when(snapshot.task()).thenReturn(task);
    when(snapshot.configurationHash()).thenReturn("synthetic-config");
    when(task.taskId()).thenReturn(id);
    var block =
        new BlockRef(
            UUID.randomUUID(),
            new BlockHeader(1, "synthetic-config", 1, 1, null, null, 0),
            new StorageRef("BLOCK", id, UUID.randomUUID(), 1L, null, null),
            1,
            "synthetic");
    var reader = mock(BlockReader.class);
    when(reader.next()).thenReturn(Optional.empty());
    when(storage.readBlock(any())).thenReturn(reader);
    var active = new AtomicBoolean(true);
    when(checkpoints.find(id))
        .thenAnswer(
            invocation -> {
              active.set(false);
              return new CheckpointSnapshot(
                  id, 1, 1, null, 0, 0, "synthetic-config", List.of(block));
            });
    var service =
        new ExportDataCollectionService(
            checkpoints,
            orders,
            storage,
            mock(com.exportflow.worker.port.AssemblyRepairRepository.class),
            new com.exportflow.worker.infrastructure.file.DataBlockCodec(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    var failure = catchThrowable(() -> service.collect(context, active::get));
    verify(storage, never()).readBlock(any());
    verifyNoInteractions(orders);
    assertThat(failure).isInstanceOf(java.io.IOException.class);
  }
}
