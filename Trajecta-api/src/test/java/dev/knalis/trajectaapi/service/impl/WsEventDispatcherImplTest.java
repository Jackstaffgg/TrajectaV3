package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.WsPayload;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WsEventDispatcherImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WsEventDispatcherImpl service;

    private record DummyPayload(String value) implements WsPayload {}

    @BeforeEach
    void setUp() {
        service = new WsEventDispatcherImpl(userRepository, messagingTemplate);
    }

    @Test
    void emitToUsers_sendsOnlyToExistingUsers() {
        User first = new User();
        first.setUsername("u1");

        when(userRepository.findById(1L)).thenReturn(Optional.of(first));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        service.emitToUsers(List.of(1L, 2L), WsEventType.NEW_NOTIFICATION, new DummyPayload("p"));

        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/events"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("u2"), eq("/queue/events"), any());
    }

    @Test
    void emitToUsersExcept_skipsExcludedUser() {
        User first = new User();
        first.setUsername("u1");

        when(userRepository.findById(1L)).thenReturn(Optional.of(first));

        service.emitToUsersExcept(List.of(1L, 2L), 2L, WsEventType.NEW_NOTIFICATION, new DummyPayload("x"));

        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/events"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("u2"), eq("/queue/events"), any());
    }
}


