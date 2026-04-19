package dev.knalis.trajectaapi.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Public user profile view.")
public class UserResponse {
    @Schema(description = "User identifier.", example = "7")
    private long id;

    @Schema(description = "Display name.", example = "Jane Operator")
    private String name;

    @Schema(description = "Username.", example = "jane_operator")
    private String username;

    @Schema(description = "Email address.", example = "jane@example.com")
    private String email;

    @Schema(description = "Role name.", example = "USER")
    private String role;

    @Schema(description = "Whether the user currently has an active ban.", example = "false")
    private boolean hasActiveBan;
}