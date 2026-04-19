package dev.knalis.trajectaapi.dto.user;

import dev.knalis.trajectaapi.model.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Payload for updateCurrentUser user role.")
public class UserRoleUpdateRequest {
    @Schema(description = "New role for the user", example = "ADMIN")
    @NotNull(message = "Role is required")
    private Role role;
}
