package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Task bulk delete request.")
public class TaskBulkDeleteRequest {

    @NotEmpty
    @Schema(description = "Task identifiers to delete.", example = "[1,2,3]")
    private List<Long> taskIds;
}

