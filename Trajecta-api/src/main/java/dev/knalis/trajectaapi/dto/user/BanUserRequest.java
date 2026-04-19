package dev.knalis.trajectaapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Payload for banning a user.")
public class BanUserRequest {
    
    @Schema(description = "Target user identifier", example = "15")
    private Long userId;
    
    @NotBlank(message = "Reason is required")
    @Schema(description = "Reason for the ban", example = "Spam and repeated abuse")
    private String reason;
    
    @Future(message = "Expiration time must be in the future")
    @Schema(
            description = "Ban expiration timestamp in UTC. Null means permanent ban.",
            example = "2026-12-31T23:59:59Z",
            nullable = true
    )
    private Instant expiredAt;
}