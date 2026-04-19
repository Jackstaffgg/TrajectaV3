package dev.knalis.trajectaapi.controller.rest.v1;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.task.*;
import dev.knalis.trajectaapi.mapper.task.TaskResponseMapper;
import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.service.impl.cache.FlightTaskDtoCacheService;
import dev.knalis.trajectaapi.service.intrf.task.FileService;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Telemetry task operations.")
@SecurityRequirement(name = "bearerAuth")
public class FlightTaskController {
    
    private final FlightTaskService flightTaskService;
    private final TaskResponseMapper taskResponseMapper;
    private final FlightTaskDtoCacheService flightTaskDtoCacheService;
    private final FileService fileService;
    private final CurrentUserResolver currentUserResolver;
    
    @Operation(summary = "Create a telemetry analysis task", description = "Uploads a BIN file and starts asynchronous trajectory analysis.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Task created and started"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid title or file"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskCreateResponse>> createTask(@Valid @ModelAttribute TaskCreateRequest request, Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        
        var task = flightTaskService.createAndStartAnalysis(
                request.getTitle(),
                request.getFile(),
                currentUser.getId(),
                request.getFolderName(),
                buildAnalysisParams(request.getTimelineDtSeconds(), request.getFolderId())
        );
        
        final var response = taskResponseMapper.toCreateResponse(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
    
    @Operation(summary = "Get task by id", description = "Returns a task if it belongs to the current user or the caller is admin.")
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@Parameter(description = "Task identifier", example = "42") @PathVariable Long taskId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(flightTaskDtoCacheService.getTaskDto(taskId, auth.getName(), auth)));
    }

    @Operation(summary = "Append AI conclusion", description = "Appends a static AI conclusion to trajectory content and marks the task as concluded.")
    @PostMapping("/{taskId}/ai-conclusion")
    public ResponseEntity<ApiResponse<TaskResponse>> addAiConclusion(@Parameter(description = "Task identifier", example = "42") @PathVariable Long taskId, Authentication auth) {
        var task = flightTaskService.addAiConclusion(taskId, auth);
        return ResponseEntity.ok(ApiResponse.success(taskResponseMapper.toDto(task)));
    }

    @Operation(summary = "Regenerate AI conclusion", description = "Forces AI conclusion regeneration and overwrites the previous conclusion.")
    @PostMapping("/{taskId}/ai-conclusion/regenerate")
    public ResponseEntity<ApiResponse<TaskResponse>> regenerateAiConclusion(@Parameter(description = "Task identifier", example = "42") @PathVariable Long taskId, Authentication auth) {
        var task = flightTaskService.regenerateAiConclusion(taskId, auth);
        return ResponseEntity.ok(ApiResponse.success(taskResponseMapper.toDto(task)));
    }
    
    @Operation(summary = "List current user tasks", description = "Returns paged tasks ordered by creation time descending.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMyTasks(
            @Parameter(description = "Offset, must be divisible by limit", example = "0")
            @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Page size in range 1..100", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth
    ) {
        var tasks = flightTaskDtoCacheService.getMyTaskDtos(auth.getName(), offset, limit, auth);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @Operation(summary = "Clone task and run re-analysis", description = "Creates a new task from source BIN with optional analysis parameter overrides.")
    @PostMapping("/{taskId}/reanalyze")
    public ResponseEntity<ApiResponse<TaskCreateResponse>> reanalyzeTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) TaskReanalyzeRequest request,
            Authentication auth
    ) {
        TaskReanalyzeRequest safeRequest = request == null ? new TaskReanalyzeRequest() : request;
        FlightTask task = flightTaskService.reanalyzeTask(
                taskId,
                safeRequest.getTitle(),
                safeRequest.getFolderName(),
                buildAnalysisParams(safeRequest.getTimelineDtSeconds(), safeRequest.getFolderId()),
                auth
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(taskResponseMapper.toCreateResponse(task)));
    }

    @Operation(summary = "Compare tasks", description = "Returns UI-ready comparison rows and deltas for selected task ids.")
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<TaskComparisonResponse>> compareTasks(@Valid @RequestBody TaskCompareRequest request, Authentication auth) {
        var response = flightTaskService.compareTasks(request.getTaskIds(), request.getBaselineTaskId(), auth);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Bulk delete tasks", description = "Deletes tasks from provided id list when caller has access.")
    @PostMapping("/delete-bulk")
    public ResponseEntity<ApiResponse<TaskBulkDeleteResponse>> deleteTasks(@Valid @RequestBody TaskBulkDeleteRequest request, Authentication auth) {
        var result = flightTaskService.deleteTasks(request.getTaskIds(), auth);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Bulk move tasks", description = "Moves selected tasks to a target folder. Use null folderId to move to root.")
    @PostMapping("/move-bulk")
    public ResponseEntity<ApiResponse<TaskBulkMoveResponse>> moveTasks(@Valid @RequestBody TaskBulkMoveRequest request, Authentication auth) {
        var result = flightTaskService.moveTasksToFolder(request.getTaskIds(), request.getFolderId(), auth);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @Operation(summary = "Download raw BIN file", description = "Streams the original uploaded BIN telemetry file.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Binary stream", content = @Content(mediaType = "application/octet-stream"))
    @GetMapping(value = "/{taskId}/raw", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadRaw(@Parameter(description = "Task identifier", example = "42") @PathVariable Long taskId, Authentication auth) {
        final var rawKey = flightTaskService.getTaskRawKey(taskId, auth);
        
        StreamingResponseBody stream = outputStream -> {
            try (var inputStream = fileService.getObjectStream(rawKey)) {
                inputStream.transferTo(outputStream);
            }
        };
        
        return buildDownloadResponse(stream, "source.bin");
    }
    
    @Operation(summary = "Download trajectory JSON file", description = "Streams trajectory analysis output as a JSON file.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Binary stream", content = @Content(mediaType = "application/octet-stream"))
    @GetMapping(value = "/{taskId}/trajectory", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadTrajectory(@Parameter(description = "Task identifier", example = "42") @PathVariable Long taskId, Authentication auth) {
        final var trajectoryKey = flightTaskService.getTaskTrajectoryKey(taskId, auth);
        
        StreamingResponseBody stream = outputStream -> {
            try (var inputStream = fileService.getObjectStream(trajectoryKey)) {
                inputStream.transferTo(outputStream);
            }
        };
        
        return buildDownloadResponse(stream, "trajectory.json");
    }

    private ResponseEntity<StreamingResponseBody> buildDownloadResponse(StreamingResponseBody stream, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    private Map<String, Object> buildAnalysisParams(Double timelineDtSeconds, Long folderId) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (timelineDtSeconds != null) {
            params.put("timelineDtSeconds", timelineDtSeconds);
        }
        if (folderId != null) {
            params.put("folderId", folderId);
        }
        return params;
    }
}
