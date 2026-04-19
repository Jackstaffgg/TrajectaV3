package dev.knalis.trajectaapi.dto.notification;

import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Admin broadcast request. When recipientIds is empty, message is sent to all users.")
public class NotificationBroadcastRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Broadcast message content.", example = "Planned maintenance at 22:00 UTC")
    private String content;

    @Schema(description = "Notification type.", example = "SYSTEM_NEWS")
    private NotificationType type = NotificationType.SYSTEM_NEWS;

    @Schema(description = "Optional target users. Empty means all users.")
    private List<Long> recipientIds;
}


