package dev.knalis.trajectaapi.controller.rest.v1;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.service.impl.cache.NotificationDtoCacheService;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification retrieval and mutation operations.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    
    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationDtoCacheService notificationDtoCacheService;
    
    @Operation(summary = "Get current user notifications", description = "Returns all notifications for the authenticated user.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);

        List<NotificationResponse> notifications = notificationDtoCacheService.getForUser(auth.getName(), currentUser.getId());

        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
    
    @Operation(summary = "Mark notification as read", description = "Marks a specific notification as read for its recipient.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@Parameter(description = "Notification identifier", example = "17") @PathVariable Long id, Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        notificationService.markAsRead(id, currentUser.getId());
        notificationDtoCacheService.evictForUser(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications of the current user as read.")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        notificationService.markAllAsRead(currentUser.getId());
        notificationDtoCacheService.evictForUser(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @Operation(summary = "Delete notification", description = "Deletes a notification owned by the authenticated recipient.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@Parameter(description = "Notification identifier", example = "17") @PathVariable Long id, Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        notificationService.deleteNotification(id, currentUser.getId());
        notificationDtoCacheService.evictForUser(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}




