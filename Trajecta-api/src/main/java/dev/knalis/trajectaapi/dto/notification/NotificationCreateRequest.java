package dev.knalis.trajectaapi.dto.notification;

import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Notification creation payload.")
public class NotificationCreateRequest {
    
    @Schema(description = "Notification recipient user id.", example = "12")
    @NotNull(message = "Recipient id is required")
    private Long recipientId;
    
    @Schema(description = "Notification type.", example = "TASK_COMPLETED")
    @NotNull(message = "Notification type is required")
    private NotificationType type;
    
    @Schema(description = "Notification text content.", example = "Task #42 has finished")
    @Size(min = 1, max = 100)
    private String content;
    
    @Schema(description = "Optional sender user id. When absent, system sender is used.", example = "3")
    private Long senderId;
    
    @Schema(description = "Optional sender display name.", example = "System")
    @Size(min = 1, max = 100)
    private String senderName;
    
    @Schema(description = "Optional domain reference id (e.g., task id).", example = "42")
    private Long referenceId;
}


