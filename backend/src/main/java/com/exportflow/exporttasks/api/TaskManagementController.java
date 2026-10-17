// 本次增量：[计划](../../../../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[HTTP契约](../../../../../../../../specs/004-task-management/contracts/http-sse.md)。
package com.exportflow.exporttasks.api;

import com.exportflow.exporttasks.api.TaskManagementApiModels.*;
import com.exportflow.exporttasks.application.TaskManagementService;
import com.exportflow.shared.domain.BusinessException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/export-tasks")
public class TaskManagementController {
  private final TaskManagementService service;
  private final TaskManagementApiMapper mapper;
  private final ObjectMapper json;

  public TaskManagementController(
      TaskManagementService service, TaskManagementApiMapper mapper, ObjectMapper json) {
    this.service = service;
    this.mapper = mapper;
    this.json =
        json.copy()
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    this.json.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
  }

  @GetMapping
  public ResponseEntity<TaskPageDto> query(@RequestParam MultiValueMap<String, String> values) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(mapper.toDto(service.query(mapper.toQuery(values))));
  }

  @PostMapping(path = "/{taskId}/pause", consumes = "application/json")
  public ResponseEntity<PauseResultDto> pause(@PathVariable UUID taskId, HttpServletRequest request)
      throws java.io.IOException {
    var result = service.pause(mapper.toPause(taskId, readControlRequest(request)));
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(new PauseResultDto(result.outcome(), mapper.toDto(result.task())));
  }

  /** 继续沿用严格控制请求协议，服务端独立裁决可用状态。 */
  @PostMapping(path = "/{taskId}/resume", consumes = "application/json")
  public ResponseEntity<ResumeResultDto> resume(
      @PathVariable UUID taskId, HttpServletRequest request) throws java.io.IOException {
    var input = mapper.toPause(taskId, readControlRequest(request));
    var result =
        service.resume(
            new com.exportflow.exporttasks.domain.TaskManagementModels.ResumeRequest(
                input.taskId(), input.operationId(), input.expectedControlVersion()));
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(new ResumeResultDto(result.outcome(), mapper.toDto(result.task())));
  }

  private PauseRequestDto readControlRequest(HttpServletRequest request)
      throws java.io.IOException {
    byte[] bytes = request.getInputStream().readNBytes(4097);
    if (bytes.length > 4096) throw BusinessException.validation();
    PauseRequestDto dto;
    try {
      dto = json.readValue(bytes, PauseRequestDto.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      throw BusinessException.validation();
    }
    return dto;
  }
}
