package dev.knalis.trajectaapi.controller.rest.v1.admin;

import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.user.BanUserRequest;
import dev.knalis.trajectaapi.dto.user.UserPunishmentResponse;
import dev.knalis.trajectaapi.mapper.UserPunishmentMapper;
import dev.knalis.trajectaapi.service.intrf.user.PunishmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/punishments")
@RequiredArgsConstructor
@Tag(name = "Admin Punishments", description = "Administrative punishment management endpoints.")
@SecurityRequirement(name = "bearerAuth")
public class AdminPunishmentController {
    
    private final PunishmentService punishmentService;
    private final UserPunishmentMapper userPunishmentMapper;
    
    @Operation(summary = "Ban user", description = "Creates a BAN punishment for a target user.")
    @PostMapping("/ban")
    public ResponseEntity<ApiResponse<UserPunishmentResponse>> banUser(
            @Valid @RequestBody BanUserRequest request,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(userPunishmentMapper.toDto(punishmentService.ban(request, auth)))
        );
    }
    
    @Operation(summary = "Unban user", description = "Marks BAN punishment as expired immediately.")
    @PostMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<Void>> unban(
            @Parameter(description = "Punishment identifier", example = "101")
            @PathVariable Long id,
            Authentication auth
    ) {
        punishmentService.unban(id, auth);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Unban user by id", description = "Finds and deactivates latest active BAN punishment for the user.")
    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanByUserId(
            @Parameter(description = "User identifier", example = "15")
            @PathVariable Long userId,
            Authentication auth
    ) {
        punishmentService.unbanByUserId(userId, auth);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
}