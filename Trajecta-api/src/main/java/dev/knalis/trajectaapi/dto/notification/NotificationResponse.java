package dev.knalis.trajectaapi.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Notification payload returned to clients.")
public class NotificationResponse {
    @Schema(description = "Notification identifier.", example = "17")
    private Long id;

    @Schema(description = "Notification type.", example = "SYSTEM_ALERT")
    private NotificationType type;
    
    @Schema(description = "Message content.", example = "Task processing has started")
    private String content;
    
    @Schema(description = "Sender user id.", example = "-1488")
    private Long senderId;

    @Schema(description = "Recipient user id.", example = "12")
    private Long recipientId;

    @Schema(description = "Sender display name.", example = "System")
    private String senderName;
    
    @Schema(description = "Optional domain reference id.", example = "42")
    private Long referenceId;
    
    @JsonProperty("isRead")
    @Schema(description = "Read flag.", example = "false")
    private boolean isRead;
    
    @Schema(description = "Creation timestamp.", example = "2026-04-03T18:10:00Z")
    private Instant createdAt;
}



