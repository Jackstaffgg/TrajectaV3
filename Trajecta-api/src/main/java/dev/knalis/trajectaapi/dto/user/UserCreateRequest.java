package dev.knalis.trajectaapi.dto.user;

import dev.knalis.trajectaapi.model.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Administrative payload for creating a new user.")
public class UserCreateRequest {
    
    @Schema(description = "Display name.", example = "Jane Operator")
    @NotBlank(message = "Name must not be blank")
    private String name;
    
    @Schema(description = "Unique username.", example = "jane_operator")
    @NotBlank(message = "Username must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must contain only letters, numbers, and underscores")
    @Size(min = 4, max = 16, message = "Username must be between 4 and 16 characters")
    private String username;
    
    @Schema(description = "Raw password to be encoded.", example = "StrongPass123")
    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;
    
    @Schema(description = "Email address.", example = "jane@example.com")
    @NotBlank
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @Schema(description = "Account role.", allowableValues = {"ADMIN", "USER"}, example = "USER")
    @NotBlank(message = "Role must not be blank")
    private Role role;
}


