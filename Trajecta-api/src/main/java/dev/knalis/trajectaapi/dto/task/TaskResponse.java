package dev.knalis.trajectaapi.dto.task;

import dev.knalis.trajectaapi.model.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Telemetry task details.")
public class TaskResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Task identifier.", example = "42")
    private Long id;

    @Schema(description = "Task title.", example = "Flight #102 telemetry")
    private String title;

    @Schema(description = "Optional logical folder used for grouping tasks.", example = "Mission A")
    private String folderName;

    @Schema(description = "Optional folder identifier.", example = "7")
    private Long folderId;

    @Schema(description = "Current task status.", example = "COMPLETED")
    private TaskStatus status;

    @Schema(description = "Failure details when status is FAILED.", example = "Trajectory file is missing in analysis result")
    private String errorMessage;

    @Schema(description = "Creation timestamp.", example = "2026-04-03T18:00:00Z")
    private Instant createdAt;

    @Schema(description = "Completion timestamp for terminal states.", example = "2026-04-03T18:01:23Z")
    private Instant finishedAt;

    @Schema(description = "AI-generated trajectory conclusion when available.", example = "Stable climb profile with minor heading oscillation.")
    private String aiConclusion;

    @Schema(description = "AI model that generated the conclusion.", example = "GPT_4O_MINI")
    private String aiModel;

    @Schema(description = "Analysis parameters used for this task.")
    private Map<String, Object> analysisParams;
}



