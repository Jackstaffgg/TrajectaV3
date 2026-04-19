package dev.knalis.trajectaapi.dto.ws.payload;

import dev.knalis.trajectaapi.dto.ws.WsPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "WebSocket payload for USER_BANNED event.")
public class UserBannedPayload implements WsPayload {
    @Schema(description = "Banned user identifier.", example = "15")
    private Long userId;

    @Schema(description = "Ban punishment identifier.", example = "101")
    private Long punishmentId;

    @Schema(description = "Ban reason.", example = "Spam and repeated abuse")
    private String reason;

    @Schema(description = "Ban expiration timestamp. Null means permanent ban.", nullable = true, example = "2026-12-31T23:59:59Z")
    private Instant expiredAt;

    @Schema(description = "Moderator identifier who issued the ban.", example = "2")
    private Long punishedById;

    @Schema(description = "Moderator display name.", example = "Jane Operator")
    private String punishedByName;

    @Schema(description = "Event timestamp.", example = "2026-04-05T10:10:00Z")
    @Builder.Default
    private Instant timestamp = Instant.now();
}

