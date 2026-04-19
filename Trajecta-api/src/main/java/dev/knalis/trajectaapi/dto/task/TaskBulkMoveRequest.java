package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Bulk move request for assigning tasks to a folder.")
public class TaskBulkMoveRequest {

    @NotEmpty(message = "Task ids must not be empty")
    @Schema(description = "Task ids to move.", example = "[12, 13]")
    private List<@NotNull @Positive Long> taskIds;

    @Schema(description = "Target folder id. Null means move to root (no folder).", example = "7")
    @Positive(message = "Folder id must be positive")
    private Long folderId;
}

