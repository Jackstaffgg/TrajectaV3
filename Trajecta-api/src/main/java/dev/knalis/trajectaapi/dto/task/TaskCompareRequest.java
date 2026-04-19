package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request payload for task metrics comparison.")
public class TaskCompareRequest {

    @NotEmpty(message = "taskIds must not be empty")
    @Schema(description = "Task identifiers to compare.", example = "[42, 43]")
    private List<@NotNull Long> taskIds;

    @Schema(description = "Optional baseline task id. If absent, the first id from taskIds is used.", example = "42")
    private Long baselineTaskId;
}

