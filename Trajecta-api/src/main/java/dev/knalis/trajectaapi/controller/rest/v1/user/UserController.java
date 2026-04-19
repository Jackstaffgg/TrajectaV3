package dev.knalis.trajectaapi.controller.rest.v1.user;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.user.BanStatusResponse;
import dev.knalis.trajectaapi.dto.user.UserResponse;
import dev.knalis.trajectaapi.dto.user.UserUpdateRequest;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.service.intrf.user.PunishmentService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current authenticated user profile endpoints.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private final UserService userService;
    private final PunishmentService punishmentService;
    private final UserMapper userMapper;
    private final CurrentUserResolver currentUserResolver;
    
    @Operation(summary = "Get current user profile", description = "Returns profile data of the authenticated user.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(currentUser)));
    }

    @Operation(summary = "Get current user ban status", description = "Returns active BAN details for the authenticated user when present.")
    @GetMapping("/me/ban-status")
    public ResponseEntity<ApiResponse<BanStatusResponse>> getCurrentUserBanStatus(Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        var activeBan = punishmentService.getActiveBan(currentUser.getId());

        if (activeBan.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(BanStatusResponse.builder().banned(false).build()));
        }

        var ban = activeBan.get();
        return ResponseEntity.ok(ApiResponse.success(BanStatusResponse.builder()
                .banned(true)
                .punishmentId(ban.getId())
                .reason(ban.getReason())
                .expiredAt(ban.getExpiredAt())
                .punishedById(ban.getPunishedBy().getId())
                .punishedByName(ban.getPunishedBy().getName())
                .build()));
    }
    
    @Operation(summary = "Update current user profile", description = "Updates mutable profile fields for the authenticated user.")
    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(@Valid @RequestBody UserUpdateRequest updateRequest, Authentication auth) {
        var updatedUser = userService.updateCurrentUser(auth, updateRequest);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(updatedUser)));
    }
}


