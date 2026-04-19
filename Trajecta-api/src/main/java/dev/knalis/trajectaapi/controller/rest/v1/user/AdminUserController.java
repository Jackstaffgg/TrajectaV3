package dev.knalis.trajectaapi.controller.rest.v1.user;

import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.user.AdminUserDetailsResponse;
import dev.knalis.trajectaapi.dto.user.AdminUserStatsResponse;
import dev.knalis.trajectaapi.dto.user.UserResponse;
import dev.knalis.trajectaapi.dto.user.UserRoleUpdateRequest;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.mapper.UserPunishmentMapper;
import dev.knalis.trajectaapi.service.intrf.user.PunishmentService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users", description = "Administrative user management endpoints.")
public class AdminUserController {
    
    private final UserService userService;
    private final PunishmentService punishmentService;
    private final UserMapper userMapper;
    private final UserPunishmentMapper userPunishmentMapper;
    
    @Operation(summary = "List all users", description = "Returns every user account. Intended for administrators.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        final var users = userMapper.toDtoList(userService.findAll(page, size));
        users.forEach(user -> user.setHasActiveBan(punishmentService.isUserBanned(user.getId())));

        return ResponseEntity.ok(
                ApiResponse.success(users)
        );
    }
    
    @Operation(summary = "Update user role", description = "Updates a user account's role by identifier.")
    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @Parameter(description = "User identifier", example = "3") @PathVariable Long id,
            @Valid @RequestBody UserRoleUpdateRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(userMapper.toDto(userService.updateRole(id, request.getRole(), auth)))
        );
    }
    
    @Operation(summary = "Delete user by id", description = "Deletes a user account by identifier.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "User identifier", example = "3") @PathVariable Long id,
            Authentication auth
    ) {
        userService.delete(id, auth);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Get users stats", description = "Returns aggregated counters for admin dashboard cards.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminUserStatsResponse>> getUsersStats() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAdminUserStats()));
    }
    
    @Operation(summary = "Get user by id", description = "Returns a single user profile by identifier with active punishments.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailsResponse>> getUserById(
            @Parameter(description = "User identifier", example = "3") @PathVariable Long id
    ) {
        final var user = userService.findById(id);
        final var response = userMapper.toAdminDetailsDto(user);
        response.setActivePunishments(
                punishmentService.getActivePunishments(id).stream()
                        .map(userPunishmentMapper::toDto)
                        .toList()
        );
        response.setPunishmentHistory(
                punishmentService.getPunishmentsHistory(id).stream()
                        .map(userPunishmentMapper::toDto)
                        .toList()
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}