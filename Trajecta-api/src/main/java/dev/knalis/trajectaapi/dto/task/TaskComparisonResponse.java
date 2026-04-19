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
@Schema(description = "Comparison payload for selected task metrics.")
public class TaskComparisonResponse {

    @Schema(description = "Baseline task identifier used for delta calculation.", example = "42")
    private Long baselineTaskId;

    @Schema(description = "Compared task rows with values and deltas.")
    private List<TaskComparisonItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskComparisonItem {
        private Long taskId;
        private String title;
        private String folderName;
        private String status;
        private MetricsValues metrics;
        private MetricsDelta delta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsValues {
        private Double maxAltitude;
        private Double maxSpeed;
        private Double flightDuration;
        private Double distance;
        private Double maxVerticalSpeed;
        private Double endpointErrorM;
        private Double rmsCrosstrackErrorM;
        private Double ateM;
        private Double p95CrosstrackErrorM;
        private Integer pointCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsDelta {
        private Double maxAltitude;
        private Double maxSpeed;
        private Double flightDuration;
        private Double distance;
        private Double maxVerticalSpeed;
        private Double endpointErrorM;
        private Double rmsCrosstrackErrorM;
        private Double ateM;
        private Double p95CrosstrackErrorM;
        private Integer pointCount;
    }
}

