package dev.knalis.trajectaapi.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Aggregated service health for admin panel.")
public class ServiceHealthResponse {

    @Schema(description = "Overall service status.", example = "UP")
    private String status;

    @Schema(description = "Database reachability status.", example = "UP")
    private String database;

    @Schema(description = "Redis reachability status.", example = "UP")
    private String redis;

    @Schema(description = "Failure detail for database check when status is DOWN.")
    private String databaseError;

    @Schema(description = "Failure detail for Redis check when status is DOWN.")
    private String redisError;

    @Schema(description = "Timestamp when status was sampled.")
    private Instant checkedAt;
}

