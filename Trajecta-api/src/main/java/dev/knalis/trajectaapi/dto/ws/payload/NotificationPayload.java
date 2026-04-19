package dev.knalis.trajectaapi.dto.ws.payload;

import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.dto.ws.WsPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "WebSocket payload for NEW_NOTIFICATION event.")
public class NotificationPayload implements WsPayload {
    @Schema(description = "Notification data object.")
    private NotificationResponse notification;
}


