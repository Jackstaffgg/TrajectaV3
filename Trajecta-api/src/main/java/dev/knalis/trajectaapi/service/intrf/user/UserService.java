package dev.knalis.trajectaapi.service.intrf.user;

import dev.knalis.trajectaapi.dto.auth.RegisterRequest;
import dev.knalis.trajectaapi.dto.user.AdminUserStatsResponse;
import dev.knalis.trajectaapi.dto.user.UserUpdateRequest;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * User account management service.
 */
public interface UserService {
    /** Registers a new self-service user account. */
    User register(RegisterRequest request);
    
    /** Updates role of target user. */
    User updateRole(long targetId, Role role, Authentication auth);

    /** Finds user by username. */
    User findByUsername(String username);

    /** Finds user by identifier. */
    User findById(long id);
    
    /** Returns all users. */
    List<User> findAll();
    
    /** Returns all users with pagination. */
    List<User> findAll(int page, int size);
    
    /** Deletes user by identifier. */
    void delete(long targetId, Authentication auth);

    /** Updates mutable fields of user profile. */
    User updateCurrentUser(Authentication auth, UserUpdateRequest userUpdateRequest);

    /** Returns aggregate user counters for the admin dashboard. */
    AdminUserStatsResponse getAdminUserStats();
    
}


