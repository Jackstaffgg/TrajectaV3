package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.notification.NotificationCreateRequest;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.mapper.NotificationMapper;
import dev.knalis.trajectaapi.model.notiffication.Notification;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import dev.knalis.trajectaapi.repo.NotificationRepository;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private WsEventDispatcher wsEventDispatcher;
    @Mock
    private NotificationMapper notificationMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, wsEventDispatcher, notificationMapper);
    }

    @Test
    void createNotification_usesSystemSenderWhenSenderNull() {
        NotificationCreateRequest req = new NotificationCreateRequest();
        req.setRecipientId(5L);
        req.setType(NotificationType.SYSTEM_ALERT);
        req.setContent("hello");

        when(notificationMapper.toDto(any())).thenReturn(new NotificationResponse());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.createNotification(req);

        assertThat(saved.getSenderId()).isEqualTo(-1488L);
        assertThat(saved.getSenderName()).isEqualTo("System");
        verify(wsEventDispatcher).emitToUsers(eq(List.of(5L)), eq(WsEventType.NEW_NOTIFICATION), any());
    }

    @Test
    void markAsRead_deniesOtherRecipient() {
        Notification n = new Notification();
        n.setId(1L);
        n.setRecipientId(10L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markAsRead(1L, 12L)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void deleteNotification_callsRepositoryDeleteForOwner() {
        Notification n = new Notification();
        n.setId(3L);
        n.setRecipientId(7L);
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(n));

        service.deleteNotification(3L, 7L);

        verify(notificationRepository).deleteById(3L);
    }

    @Test
    void createNotification_usesProvidedSenderWhenPresent() {
        NotificationCreateRequest req = new NotificationCreateRequest();
        req.setRecipientId(5L);
        req.setType(NotificationType.TASK_COMPLETED);
        req.setContent("done");
        req.setSenderId(99L);
        req.setSenderName("alice");

        when(notificationMapper.toDto(any())).thenReturn(new NotificationResponse());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.createNotification(req);

        assertThat(saved.getSenderId()).isEqualTo(99L);
        assertThat(saved.getSenderName()).isEqualTo("alice");
    }

    @Test
    void markAsRead_marksAndSavesForOwner() {
        Notification n = new Notification();
        n.setId(5L);
        n.setRecipientId(10L);
        n.setRead(false);
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));

        service.markAsRead(5L, 10L);

        assertThat(n.isRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void markAllAsRead_marksEachUnreadAndSavesAll() {
        Notification first = new Notification();
        Notification second = new Notification();
        first.setRead(false);
        second.setRead(false);

        when(notificationRepository.findByRecipientIdAndIsRead(7L, false)).thenReturn(List.of(first, second));

        service.markAllAsRead(7L);

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(first, second));
    }

    @Test
    void deleteNotification_deniesOtherRecipient() {
        Notification n = new Notification();
        n.setId(6L);
        n.setRecipientId(1L);
        when(notificationRepository.findById(6L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.deleteNotification(6L, 2L)).isInstanceOf(PermissionDeniedException.class);
    }
}

