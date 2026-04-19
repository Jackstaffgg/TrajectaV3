package dev.knalis.trajectaapi.dto.messaging;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Computed metrics from trajectory analysis.")
public class AnalysisMetrics {
    @Schema(description = "Maximum altitude in meters.", example = "1240.5")
    private Double maxAltitude;

    @Schema(description = "Maximum speed in meters per second.", example = "72.3")
    private Double maxSpeed;

    @Schema(description = "Flight duration in seconds.", example = "542.1")
    private Double flightDuration;

    @Schema(description = "Total traveled distance in meters.", example = "15432.8")
    private Double distance;

    @Schema(description = "Maximum vertical speed in meters per second.", example = "31.6")
    private Double maxVerticalSpeed;

    @Schema(description = "Endpoint error in meters against ground truth.", example = "4.9")
    private Double endpointErrorM;

    @Schema(description = "RMS cross-track error in meters.", example = "2.1")
    private Double rmsCrosstrackErrorM;

    @Schema(description = "Average trajectory error in meters.", example = "1.8")
    private Double ateM;

    @Schema(description = "95th percentile cross-track error in meters.", example = "3.7")
    private Double p95CrosstrackErrorM;

    @Schema(description = "Trajectory point count.", example = "987")
    private Integer pointCount;

    @Schema(description = "Whether ground truth comparison was used.", example = "true")
    private Boolean groundTruthUsed;
}


