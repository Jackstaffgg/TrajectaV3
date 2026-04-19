package dev.knalis.trajectaapi.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Cache and Redis health status.")
public class CacheHealthResponse {

    @Schema(description = "High-level status.", example = "UP")
    private String status;

    @Schema(description = "Redis ping response.", example = "PONG")
    private String redisPing;

    @Schema(description = "Error message when status is DOWN.")
    private String error;
}

