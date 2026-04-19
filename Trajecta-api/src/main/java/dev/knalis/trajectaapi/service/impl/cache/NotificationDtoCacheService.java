package dev.knalis.trajectaapi.service.impl.cache;

import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationDtoCacheService {

    private final NotificationService notificationService;

    @Cacheable(cacheNames = "notificationDtoByUser", key = "#username")
    public List<NotificationResponse> getForUser(String username, Long userId) {
        return notificationService.getUserNotifications(userId);
    }

    @CacheEvict(cacheNames = "notificationDtoByUser", key = "#username")
    public void evictForUser(String username) {
    }
}

