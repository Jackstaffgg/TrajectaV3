package dev.knalis.trajectaapi.service.intrf.auth;

import dev.knalis.trajectaapi.dto.auth.AuthResponse;
import dev.knalis.trajectaapi.dto.auth.RegisterRequest;

/**
 * Authentication service for login and registration flows.
 */
public interface AuthService {
    /** Authenticates user credentials and returns token plus profile data. */
    AuthResponse login(String username, String password);

    /** Registers a new account and returns token plus profile data. */
    AuthResponse register(RegisterRequest request);
}


