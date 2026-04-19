package dev.knalis.trajectaapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Authentication request payload.")
public class AuthRequest {
    @Schema(description = "Unique username.", example = "pilot_01")
    @NotBlank(message = "Username must not be blank")
    private String username;
    
    @Schema(description = "Raw account password.", example = "StrongPass123")
    @NotBlank(message = "Password must not be blank")
    private String password;
}



