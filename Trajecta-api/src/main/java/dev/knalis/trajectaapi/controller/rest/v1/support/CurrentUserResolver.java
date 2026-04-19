package dev.knalis.trajectaapi.controller.rest.v1.support;

import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.model.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    public User requireUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new PermissionDeniedException("User is not authenticated");
        }
        return user;
    }
}


