package dev.knalis.trajectaapi.dto.ws.payload;

import dev.knalis.trajectaapi.dto.ws.WsPayload;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "WebSocket payload for TASK_STATUS_UPDATE event.")
public class TaskUpdateStatusPayload implements WsPayload {
    @Schema(description = "Task identifier.", example = "42")
    private Long taskId;

    @Schema(description = "New task status.", example = "COMPLETED")
    private TaskStatus taskStatus;

    @Schema(description = "Task title.", example = "Flight #102 telemetry")
    private String taskTitle;

    @Schema(description = "Optional informational or error message.", example = "Task completed successfully")
    private String message;

    @Schema(description = "Event timestamp.", example = "2026-04-03T18:15:00Z")
    @Builder.Default
    private Instant timestamp = Instant.now();
}


