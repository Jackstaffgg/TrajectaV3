package dev.knalis.trajectaapi.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Runtime metrics snapshot for admin dashboard.")
public class ServiceRuntimeMetricsResponse {

    @Schema(description = "Overall service state.", example = "UP")
    private String status;

    @Schema(description = "Stability label computed from runtime indicators.", example = "STABLE")
    private String stability;

    @Schema(description = "Service uptime in seconds.", example = "3600")
    private long uptimeSeconds;

    @Schema(description = "Current JVM live thread count.", example = "42")
    private int activeThreads;

    @Schema(description = "Heap used in MB.", example = "512")
    private long heapUsedMb;

    @Schema(description = "Heap max in MB.", example = "1024")
    private long heapMaxMb;

    @Schema(description = "Total users in system.", example = "120")
    private long usersTotal;

    @Schema(description = "Total tasks in system.", example = "420")
    private long tasksTotal;

    @Schema(description = "Failed tasks in system.", example = "21")
    private long tasksFailed;

    @Schema(description = "Task failure rate percent.", example = "5.0")
    private double taskFailureRatePct;

    @Schema(description = "Snapshot timestamp.")
    private Instant checkedAt;
}

