package dev.knalis.trajectaapi.model.user;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    USER,
    ADMIN,
    OWNER;
    
    @Override
    public @NonNull String getAuthority() {
        return "ROLE_" + this.name();
    }
}


