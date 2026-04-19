package dev.knalis.trajectaapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Current authenticated user ban status.")
public class BanStatusResponse {

    @Schema(description = "True when user has active BAN punishment.", example = "true")
    private boolean banned;

    @Schema(description = "Active punishment id.", example = "101", nullable = true)
    private Long punishmentId;

    @Schema(description = "Ban reason.", example = "Spam and repeated abuse", nullable = true)
    private String reason;

    @Schema(description = "Ban expiration timestamp. Null means permanent ban.", example = "2026-12-31T23:59:59Z", nullable = true)
    private Instant expiredAt;

    @Schema(description = "Moderator id.", example = "2", nullable = true)
    private Long punishedById;

    @Schema(description = "Moderator display name.", example = "Jane Operator", nullable = true)
    private String punishedByName;
}
