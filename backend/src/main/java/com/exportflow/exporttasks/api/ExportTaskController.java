// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
// 职责：任务受理和文件下载的HTTP边界；本期无详情/状态查询。
package com.exportflow.exporttasks.api;

import static com.exportflow.exporttasks.api.ExportTaskApiModels.*;

import com.exportflow.exporttasks.application.CreateExportTaskService;
import com.exportflow.shared.domain.BusinessException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 字节预算及完整尾部先校验，HTTP不等待MQ/Excel；设计 contracts/http-api.md，验证 TC-214/216/219。 */
@RestController
@RequestMapping("/api/v1/export-tasks")
public class ExportTaskController {
  private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
  private final CreateExportTaskService service;
  private final ExportTaskApiMapper mapper;
  private final ObjectMapper json;
  private final com.exportflow.exporttasks.application.DownloadExportFileService downloads;

  public ExportTaskController(
      CreateExportTaskService service,
      ExportTaskApiMapper mapper,
      ObjectMapper json,
      com.exportflow.exporttasks.application.DownloadExportFileService downloads) {
    this.service = service;
    this.mapper = mapper;
    this.json = json.copy();
    this.downloads = downloads;
    this.json.enable(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    this.json.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
  }

  @PostMapping(consumes = "application/json")
  public ResponseEntity<CreateTaskResponse> create(HttpServletRequest request)
      throws java.io.IOException {
    var keys = Collections.list(request.getHeaders("Idempotency-Key"));
    if (keys.size() != 1) throw BusinessException.validation();
    byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES)
      throw new BusinessException("REQUEST_TOO_LARGE", "请求过大，请缩小导出范围", 413);
    CreateTaskRequestDto dto;
    try {
      dto = json.readValue(body, CreateTaskRequestDto.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw BusinessException.validation();
    }
    var result = service.create(mapper.toCommand(dto, keys.getFirst()));
    return ResponseEntity.status(result.isReplay() ? 200 : 201)
        .header("Cache-Control", "no-store")
        .body(mapper.toResponse(result.task()));
  }

  @GetMapping("/{taskId}/file")
  public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody>
      download(@PathVariable java.util.UUID taskId) {
    var handle = downloads.prepare(taskId);
    var metadata = handle.metadata();
    String encoded =
        java.net.URLEncoder.encode(metadata.fileName(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("+", "%20");
    if (metadata.fileName().contains("\r") || metadata.fileName().contains("\n")) {
      try {
        handle.close();
      } catch (java.io.IOException ignored) {
      }
      throw new BusinessException("EXPORT_FILE_INVALID", "文件信息异常", 500);
    }
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "attachment; filename=\"orders_" + taskId + ".xlsx\"; filename*=UTF-8''" + encoded)
        .contentType(org.springframework.http.MediaType.parseMediaType(metadata.mimeType()))
        .contentLength(metadata.length())
        .body(handle::writeTo);
  }
}
