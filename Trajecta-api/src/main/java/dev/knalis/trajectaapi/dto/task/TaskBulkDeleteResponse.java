package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Task bulk delete result.")
public class TaskBulkDeleteResponse {

    @Schema(description = "Successfully deleted task ids.")
    private List<Long> deletedTaskIds;

    @Schema(description = "Skipped task ids (not found or no permission).")
    private List<Long> skippedTaskIds;
}

