package dev.knalis.trajectaapi.controller.rest.v1.admin;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.notification.NotificationBroadcastRequest;
import dev.knalis.trajectaapi.mapper.NotificationMapper;
import dev.knalis.trajectaapi.model.notiffication.Notification;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationControllerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private UserService userService;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private Authentication authentication;

    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminNotificationController(notificationService, userService, currentUserResolver, notificationMapper);
    }

    @Test
    void broadcast_sendsToAllUsersExceptAdminWhenRecipientListEmpty() {
        User admin = new User();
        admin.setId(10L);
        admin.setName("Admin");

        User u1 = new User();
        u1.setId(1L);
        User u2 = new User();
        u2.setId(2L);

        NotificationBroadcastRequest request = new NotificationBroadcastRequest();
        request.setContent("Maintenance notice");
        request.setType(NotificationType.SYSTEM_ALERT);
        request.setRecipientIds(List.of());

        when(currentUserResolver.requireUser(authentication)).thenReturn(admin);
        when(userService.findAll()).thenReturn(List.of(admin, u1, u2));

        var response = controller.broadcast(request, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(2);
        verify(notificationService, times(2)).createNotification(any());
    }

    @Test
    void broadcast_sendsOnlyToProvidedRecipients() {
        User admin = new User();
        admin.setId(10L);
        admin.setName("Admin");

        NotificationBroadcastRequest request = new NotificationBroadcastRequest();
        request.setContent("Manual alert");
        request.setType(NotificationType.SYSTEM_ALERT);
        request.setRecipientIds(List.of(3L, 4L));

        User u3 = new User();
        u3.setId(3L);
        User u4 = new User();
        u4.setId(4L);

        when(currentUserResolver.requireUser(authentication)).thenReturn(admin);
        when(userService.findAll()).thenReturn(List.of(admin, u3, u4));

        var response = controller.broadcast(request, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(2);
        verify(notificationService, times(2)).createNotification(any());
    }

    @Test
    void preview_returnsMissingAndResolvedTargets() {
        User admin = new User();
        admin.setId(10L);

        User u1 = new User();
        u1.setId(1L);
        User u2 = new User();
        u2.setId(2L);

        NotificationBroadcastRequest request = new NotificationBroadcastRequest();
        request.setContent("News");
        request.setRecipientIds(List.of(1L, 777L, 10L));

        when(currentUserResolver.requireUser(authentication)).thenReturn(admin);
        when(userService.findAll()).thenReturn(List.of(admin, u1, u2));

        var response = controller.preview(request, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTargetUsers()).isEqualTo(1);
        assertThat(response.getBody().getData().getMissingRecipientIds()).containsExactly(777L);
    }

    @Test
    void history_returnsMappedNotifications() {
        User admin = new User();
        admin.setId(10L);

        when(currentUserResolver.requireUser(authentication)).thenReturn(admin);
        when(notificationService.getAdminBroadcastHistory(10L, 20)).thenReturn(List.of(new Notification()));
        when(notificationMapper.toDtoList(any())).thenReturn(List.of());

        var response = controller.history(20, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(notificationService).getAdminBroadcastHistory(10L, 20);
    }
}




