package dev.knalis.trajectaapi.controller.rest.v1;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.service.impl.cache.NotificationDtoCacheService;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private NotificationDtoCacheService notificationDtoCacheService;
    @Mock
    private Authentication authentication;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService, currentUserResolver, notificationDtoCacheService);
    }

    @Test
    void getMyNotifications_returnsMappedList() {
        User user = new User();
        user.setId(10L);
        when(currentUserResolver.requireUser(authentication)).thenReturn(user);
        when(authentication.getName()).thenReturn("alice");
        when(notificationDtoCacheService.getForUser("alice", 10L)).thenReturn(List.of(new NotificationResponse()));

        var response = controller.getMyNotifications(authentication);

        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void markAsRead_delegatesToService() {
        User user = new User();
        user.setId(10L);
        when(currentUserResolver.requireUser(authentication)).thenReturn(user);
        when(authentication.getName()).thenReturn("alice");

        var response = controller.markAsRead(5L, authentication);

        verify(notificationService).markAsRead(5L, 10L);
        verify(notificationDtoCacheService).evictForUser("alice");
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void markAllAsRead_delegatesToService() {
        User user = new User();
        user.setId(11L);
        when(currentUserResolver.requireUser(authentication)).thenReturn(user);
        when(authentication.getName()).thenReturn("bob");

        var response = controller.markAllAsRead(authentication);

        verify(notificationService).markAllAsRead(11L);
        verify(notificationDtoCacheService).evictForUser("bob");
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void deleteNotification_delegatesToService() {
        User user = new User();
        user.setId(12L);
        when(currentUserResolver.requireUser(authentication)).thenReturn(user);
        when(authentication.getName()).thenReturn("charlie");

        var response = controller.deleteNotification(9L, authentication);

        verify(notificationService).deleteNotification(9L, 12L);
        verify(notificationDtoCacheService).evictForUser("charlie");
        assertThat(response.getBody().isSuccess()).isTrue();
    }
}

