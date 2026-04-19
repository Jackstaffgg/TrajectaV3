package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk move result.")
public class TaskBulkMoveResponse {

    @Schema(description = "Task ids successfully moved.", example = "[12]")
    private List<Long> movedTaskIds;

    @Schema(description = "Task ids skipped due to missing access or not found.", example = "[13]")
    private List<Long> skippedTaskIds;
}

