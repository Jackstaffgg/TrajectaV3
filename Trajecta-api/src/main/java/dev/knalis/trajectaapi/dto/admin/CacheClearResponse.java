package dev.knalis.trajectaapi.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Result of admin cache clear operation.")
public class CacheClearResponse {

    @Schema(description = "Target user id for cache eviction.", example = "42")
    private Long userId;

    @Schema(description = "Whether user entity exists in database.", example = "true")
    private boolean userExists;

    @Schema(description = "Whether username key was evicted.", example = "true")
    private boolean usernameKeyEvicted;
}

