package dev.knalis.trajectaapi.dto.auth;

import dev.knalis.trajectaapi.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Authentication response containing JWT and user profile.")
public class AuthResponse {
    @Schema(description = "JWT access token.", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Authenticated user profile.")
    private UserResponse user;
}



