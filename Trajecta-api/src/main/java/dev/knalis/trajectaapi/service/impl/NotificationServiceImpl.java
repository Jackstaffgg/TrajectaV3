package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.notification.NotificationCreateRequest;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.payload.NotificationPayload;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.mapper.NotificationMapper;
import dev.knalis.trajectaapi.model.notiffication.Notification;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import dev.knalis.trajectaapi.repo.NotificationRepository;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    
    private final NotificationRepository notificationRepository;
    private static final Long SYSTEM_ID = -1488L;
    private final WsEventDispatcher wsEventDispatcher;
    private final NotificationMapper notificationMapper;
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    
    @Override
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationMapper.toDtoList(notificationRepository.findByRecipientId(userId));
    }
    
    @Override
    public Notification createNotification(NotificationCreateRequest request) {
        final var notification = new Notification();
        
        notification.setReferenceId(request.getReferenceId());
        notification.setType(request.getType());
        notification.setContent(request.getContent());
        
        if (request.getSenderId() == null) {
            notification.setSenderId(SYSTEM_ID);
            notification.setSenderName("System");
        } else {
            notification.setSenderId(request.getSenderId());
            notification.setSenderName(request.getSenderName());
        }

        notification.setRecipientId(request.getRecipientId());
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());

        final var savedNotification = notificationRepository.save(notification);
        NotificationPayload payload = new NotificationPayload(notificationMapper.toDto(savedNotification));
        wsEventDispatcher.emitToUsers(List.of(savedNotification.getRecipientId()), WsEventType.NEW_NOTIFICATION , payload);
        incrementCounter("notifications.created");

        return savedNotification;
    }
    
    @Override
    public void markAsRead(Long notificationId, Long currentUserId) {
        final var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(currentUserId)) {
            throw new PermissionDeniedException("You do not have permission to updateCurrentUser this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        incrementCounter("notifications.markAsRead.single");
    }
    
    @Override
    public void markAllAsRead(Long currentUserId) {
        final var unreadNotifications = notificationRepository.findByRecipientIdAndIsRead(currentUserId, false);
        
        for (var notification : unreadNotifications) {
            notification.setRead(true);
        }
        
        notificationRepository.saveAll(unreadNotifications);
        incrementCounterBy("notifications.markAsRead.bulk", unreadNotifications.size());
    }
    
    @Override
    public void deleteNotification(Long notificationId, Long currentUserId) {
        final var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(currentUserId)) {
            throw new PermissionDeniedException("You do not have permission to delete this notification");
        }

        notificationRepository.deleteById(notificationId);
        incrementCounter("notifications.deleted");
    }

    @Override
    public List<Notification> getAdminBroadcastHistory(Long adminId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return notificationRepository.findBySenderIdAndTypeInOrderByCreatedAtDesc(
                adminId,
                List.of(NotificationType.SYSTEM_NEWS, NotificationType.SYSTEM_ALERT),
                PageRequest.of(0, safeLimit)
        );
    }

    private void incrementCounter(String name) {
        incrementCounterBy(name, 1);
    }

    private void incrementCounterBy(String name, int amount) {
        if (meterRegistry == null || amount <= 0) {
            return;
        }
        meterRegistry.counter(name).increment(amount);
    }
}


