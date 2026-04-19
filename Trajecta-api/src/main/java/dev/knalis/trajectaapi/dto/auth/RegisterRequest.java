package dev.knalis.trajectaapi.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Registration payload for self-signup.")
public class RegisterRequest {
    
    @Schema(description = "Display name.", example = "John Pilot")
    @NotBlank(message = "Name must not be blank")
    @Size(min = 4, max = 40, message = "Name must be between 4 and 16 characters")
    private String name;
    
    @Schema(description = "Unique username.", example = "pilot_01")
    @NotBlank(message = "Username must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must contain only letters, numbers, and underscores")
    @Size(min = 4, max = 16, message = "Username must be between 4 and 16 characters")
    private String username;
    
    @Schema(description = "Plain text password.", example = "StrongPass123")
    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;
    
    @Schema(description = "User email.", example = "pilot@example.com")
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
}




