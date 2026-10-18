package com.exportflow.exporttasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exportflow.exporttasks.application.TaskDeletionService;
import com.exportflow.exporttasks.domain.ExportTaskModels.TaskStatus;
import com.exportflow.exporttasks.domain.TaskDeletionModels.*;
import com.exportflow.exporttasks.port.TaskDeletionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// [AC-096](../../../../../../../docs/product/acceptance-criteria.md#ac-096)
// [AC-100](../../../../../../../docs/product/acceptance-criteria.md#ac-100)
class TaskDeletionUnitTest {
  @Test
  void TC613_deleteRequestRejectsMissingIdentityAndNegativeVersion() {
    assertThatThrownBy(() -> new DeleteRequest(null, UUID.randomUUID(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DeleteRequest(UUID.randomUUID(), null, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DeleteRequest(UUID.randomUUID(), UUID.randomUUID(), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void TC614_serviceUsesOneRepositoryEntryPointForBothModes() {
    var taskId = UUID.randomUUID();
    var operationId = UUID.randomUUID();
    var request = new DeleteRequest(taskId, operationId, 4);
    var expected =
        new DeleteResult(
            "ACCEPTED", taskId, operationId, DeletionMode.ACTIVE_STOP, DeletionState.COMPLETED, 5,
            TaskStatus.RUNNING);
    var repository = new RecordingRepository(expected);

    assertThat(new TaskDeletionService(repository).delete(request)).isEqualTo(expected);
    assertThat(repository.received).isEqualTo(request);
  }

  private static final class RecordingRepository implements TaskDeletionRepository {
    private final DeleteResult result;
    private DeleteRequest received;

    private RecordingRepository(DeleteResult result) {
      this.result = result;
    }

    @Override
    public DeleteResult delete(DeleteRequest request) {
      received = request;
      return result;
    }
  }
}
