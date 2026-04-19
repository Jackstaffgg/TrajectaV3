package dev.knalis.trajectaapi.dto.messaging;

import dev.knalis.trajectaapi.model.task.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Worker analysis result payload mapped to task completion flow.")
public class AnalysisResult {
    
    @Schema(description = "Task identifier.", example = "42")
    private Long taskId;
    
    @Schema(description = "Final analysis status.", example = "COMPLETED")
    private AnalysisStatus status;
    
    @Schema(description = "Trajectory object key when worker uploads result to storage.", example = "tasks/1/42/result/trajectory.json")
    private String trajectoryObjectKey;

    @Schema(description = "Inline trajectory JSON alternative.")
    private String trajectoryJson;
    
    @Schema(description = "Computed telemetry metrics.")
    private AnalysisMetrics metrics;
    
    @Schema(description = "Failure reason for unsuccessful analysis.", example = "Unable to parse source BIN")
    private String errorMessage;
}


