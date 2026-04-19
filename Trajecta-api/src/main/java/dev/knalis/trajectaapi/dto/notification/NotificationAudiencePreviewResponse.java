package dev.knalis.trajectaapi.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Admin notification audience preview result.")
public class NotificationAudiencePreviewResponse {

    @Schema(description = "Whether broadcast targets all users.", example = "true")
    private boolean allUsers;

    @Schema(description = "Total users in system.", example = "42")
    private int totalUsers;

    @Schema(description = "Resolved target user count.", example = "40")
    private int targetUsers;

    @Schema(description = "Resolved target user ids.")
    private List<Long> targetUserIds;

    @Schema(description = "Requested ids that do not exist.")
    private List<Long> missingRecipientIds;
}

