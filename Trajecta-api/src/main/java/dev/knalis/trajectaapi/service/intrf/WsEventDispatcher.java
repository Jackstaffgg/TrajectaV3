package dev.knalis.trajectaapi.service.intrf;

import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.WsPayload;

import java.util.List;

/**
 * WebSocket event dispatch contract.
 */
public interface WsEventDispatcher {
    /** Sends event payload to all provided users. */
    void emitToUsers(List<Long> userIds, WsEventType type, WsPayload payload);

    /** Sends event payload to users excluding one user id. */
    void emitToUsersExcept(List<Long> userIds, Long excludedUserId, WsEventType type, Object payload);
}


