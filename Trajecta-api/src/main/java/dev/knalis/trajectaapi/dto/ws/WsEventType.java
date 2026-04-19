package dev.knalis.trajectaapi.dto.ws;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "WebSocket event type.")
public enum WsEventType {
    NEW_NOTIFICATION,
    TASK_STATUS_UPDATE,
    USER_BANNED, USER_UNBANNED,
}


