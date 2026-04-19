package dev.knalis.trajectaapi.dto.user;

import dev.knalis.trajectaapi.model.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Detailed administrative user profile view.")
public class AdminUserDetailsResponse {
    
    @Schema(description = "User identifier.", example = "7")
    private long id;
    
    @Schema(description = "Display name.", example = "Jane Operator")
    private String name;
    
    @Schema(description = "Username.", example = "jane_operator")
    private String username;
    
    @Schema(description = "Email address.", example = "jane@example.com")
    private String email;
    
    @Schema(description = "Role name.", example = "USER")
    private Role role;
    
    @Schema(description = "List of active punishments for the user.")
    private List<UserPunishmentResponse> activePunishments;

    @Schema(description = "Full punishment history for the user, sorted by most recent first.")
    private List<UserPunishmentResponse> punishmentHistory;
}