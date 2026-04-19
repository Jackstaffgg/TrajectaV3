package dev.knalis.trajectaapi.controller.rest.v1.admin;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.notification.NotificationAudiencePreviewResponse;
import dev.knalis.trajectaapi.dto.notification.NotificationBroadcastRequest;
import dev.knalis.trajectaapi.dto.notification.NotificationCreateRequest;
import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.mapper.NotificationMapper;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications", description = "Administrative notification broadcast operations.")
@SecurityRequirement(name = "bearerAuth")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationMapper notificationMapper;

    @Operation(summary = "Broadcast notification", description = "Sends a notification to selected users or to all users when recipient list is empty.")
    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<Integer>> broadcast(@Valid @RequestBody NotificationBroadcastRequest request, Authentication auth) {
        var admin = currentUserResolver.requireUser(auth);
        List<Long> targetUserIds = resolveTargetUserIds(request, admin, userService.findAll());

        int sent = 0;
        for (Long recipientId : targetUserIds) {
            if (recipientId == null) {
                continue;
            }

            NotificationCreateRequest item = new NotificationCreateRequest();
            item.setRecipientId(recipientId);
            item.setType(request.getType());
            item.setContent(request.getContent());
            item.setSenderId(admin.getId());
            item.setSenderName(admin.getName());
            item.setReferenceId(null);
            notificationService.createNotification(item);
            sent += 1;
        }

        return ResponseEntity.ok(ApiResponse.success(sent));
    }

    @Operation(summary = "Preview broadcast audience", description = "Resolves recipient ids that will receive admin broadcast.")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<NotificationAudiencePreviewResponse>> preview(@Valid @RequestBody NotificationBroadcastRequest request, Authentication auth) {
        var admin = currentUserResolver.requireUser(auth);
        List<User> allUsers = userService.findAll();
        List<Long> targetUserIds = resolveTargetUserIds(request, admin, allUsers);

        List<Long> allIds = allUsers.stream().map(User::getId).toList();
        List<Long> requested = request.getRecipientIds() == null ? List.of() : request.getRecipientIds();
        List<Long> missing = requested.stream()
                .filter(id -> id != null && !allIds.contains(id) && !id.equals(admin.getId()))
                .distinct()
                .collect(Collectors.toList());

        NotificationAudiencePreviewResponse response = NotificationAudiencePreviewResponse.builder()
                .allUsers(requested.isEmpty())
                .totalUsers(allUsers.size())
                .targetUsers(targetUserIds.size())
                .targetUserIds(targetUserIds)
                .missingRecipientIds(missing)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get admin broadcast history", description = "Returns recent system news/alerts sent by current admin.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> history(
            @RequestParam(defaultValue = "50") int limit,
            Authentication auth
    ) {
        var admin = currentUserResolver.requireUser(auth);
        var history = notificationService.getAdminBroadcastHistory(admin.getId(), limit);
        return ResponseEntity.ok(ApiResponse.success(notificationMapper.toDtoList(history)));
    }

    private List<Long> resolveTargetUserIds(NotificationBroadcastRequest request, User admin, List<User> allUsers) {
        if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
            return allUsers.stream()
                    .map(User::getId)
                    .filter(id -> !id.equals(admin.getId()))
                    .collect(Collectors.toList());
        }

        List<Long> allIds = allUsers.stream().map(User::getId).collect(Collectors.toList());
        return request.getRecipientIds().stream()
                .filter(id -> id != null && !id.equals(admin.getId()) && allIds.contains(id))
                .distinct()
                .collect(Collectors.toList());
    }
}



