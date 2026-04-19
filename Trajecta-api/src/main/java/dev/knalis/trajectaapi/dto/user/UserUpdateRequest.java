package dev.knalis.trajectaapi.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
@Schema(description = "Payload for partial user profile updates.")
public class UserUpdateRequest {
    
    @Schema(description = "Display name.", example = "Jane Updated")
    @Size(min = 4, max = 40, message = "Name must be between 4 and 40 characters")
    private String name;
    
    @Schema(description = "Username.", example = "jane_updated")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must contain only letters, numbers, and underscores")
    @Size(min = 4, max = 16, message = "Username must be between 4 and 16 characters")
    private String username;
    
    @Schema(description = "New password.", example = "NewStrongPass123")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;
    
    @Schema(description = "Old password.", example = "OldStrongPass123")
    @Size(message = "Password must be at least 8 characters long")
    private String oldPassword;
    
    @Schema(description = "Email address.", example = "jane.updated@example.com")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @JsonIgnore
    @AssertTrue
    public boolean isPasswordChangeValid() {
        if (password != null) {
            return oldPassword != null && !oldPassword.trim().isEmpty();
        }
        return true;
    }
    
    @JsonIgnore
    @AssertTrue(message = "Name must not be blank when provided")
    public boolean isNameValidWhenProvided() {
        return isNullOrNotBlank(name);
    }
    
    @JsonIgnore
    @AssertTrue(message = "Username must not be blank when provided")
    public boolean isUsernameValidWhenProvided() {
        return isNullOrNotBlank(username);
    }
    
    @JsonIgnore
    @AssertTrue(message = "Password must not be blank when provided")
    public boolean isPasswordValidWhenProvided() {
        return isNullOrNotBlank(password);
    }
    
    @JsonIgnore
    @AssertTrue(message = "Email must not be blank when provided")
    public boolean isEmailValidWhenProvided() {
        return isNullOrNotBlank(email);
    }
    
    @JsonIgnore
    @AssertTrue(message = "At least one field must be provided")
    public boolean isAtLeastOneFieldPresent() {
        return isNotBlank(name) ||
                isNotBlank(username) ||
                isNotBlank(password) ||
                isNotBlank(email);
    }
    
    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    private boolean isNullOrNotBlank(String str) {
        return str == null || !str.trim().isEmpty();
    }
}
