package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.ws.SocketEvent;
import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.WsPayload;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.repo.UserRepository;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WsEventDispatcherImpl implements WsEventDispatcher {

    private static final String EVENTS_DESTINATION = "/queue/events";

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void emitToUsers(List<Long> userIds, WsEventType type, WsPayload payload) {
        emit(userIds, null, type, payload);
    }

    @Override
    public void emitToUsersExcept(List<Long> userIds, Long excludedUserId, WsEventType type, Object payload) {
        emit(userIds, excludedUserId, type, payload);
    }

    private void emit(List<Long> userIds, Long excludedUserId, WsEventType type, Object payload) {
        SocketEvent event = new SocketEvent(type, payload);

        for (Long userId : userIds) {
            if (excludedUserId != null && excludedUserId.equals(userId)) {
                continue;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }

            messagingTemplate.convertAndSendToUser(user.getUsername(), EVENTS_DESTINATION, event);
        }
    }
}


