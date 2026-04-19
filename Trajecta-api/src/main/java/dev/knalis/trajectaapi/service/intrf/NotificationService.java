package dev.knalis.trajectaapi.service.intrf;


import dev.knalis.trajectaapi.dto.notification.NotificationCreateRequest;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.model.notiffication.Notification;

import java.util.List;

/**
 * Notification management service for user inbox and realtime dispatch.
 */
public interface NotificationService {
    /** Returns all notifications for a user. */
    List<NotificationResponse> getUserNotifications(Long userId);

    /** Creates and emits a notification. */
    Notification createNotification(NotificationCreateRequest request);

    /** Marks a specific notification as read. */
    void markAsRead(Long notificationId, Long currentUserId);

    /** Marks all user notifications as read. */
    void markAllAsRead(Long currentUserId);

    /** Deletes a user notification by id. */
    void deleteNotification(Long notificationId, Long currentUserId);

    /** Returns recent notifications broadcast by an admin account. */
    List<Notification> getAdminBroadcastHistory(Long adminId, int limit);
}


