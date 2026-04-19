package dev.knalis.trajectaapi.dto.user;

import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "User punishment response.")
public class UserPunishmentResponse {
    
    @Schema(example = "101")
    private Long id;
    
    @Schema(example = "15")
    private Long userId;
    
    @Schema(example = "2")
    private Long punishedById;
    
    @Schema(description = "Type of punishment", example = "BAN")
    private PunishmentType type;
    
    @Schema(example = "Spam and repeated abuse")
    private String reason;
    
    @Schema(example = "2026-04-05T12:00:00Z")
    private Instant createdAt;
    
    @Schema(example = "2026-04-12T12:00:00Z", nullable = true)
    private Instant expiredAt;
    
    @Schema(example = "false")
    private boolean expired;
}