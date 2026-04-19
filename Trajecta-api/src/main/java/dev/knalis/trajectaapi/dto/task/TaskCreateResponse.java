package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Task creation result.")
public class TaskCreateResponse {
    
    @Schema(description = "Created task identifier.", example = "42")
    private Long id;

    @Schema(description = "Created task title.", example = "Flight #102 telemetry")
    private String title;

    @Schema(description = "Optional logical folder used for grouping tasks.", example = "Mission A")
    private String folderName;

    @Schema(description = "Optional folder identifier.", example = "7")
    private Long folderId;

    @Schema(description = "Initial task status.", example = "PROCESSING")
    private String status;

    @Schema(description = "Source task id when this task was created by re-analysis.", example = "42")
    private Long sourceTaskId;
}


