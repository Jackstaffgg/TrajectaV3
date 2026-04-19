package dev.knalis.trajectaapi.service.impl.auth;

import dev.knalis.trajectaapi.dto.auth.RegisterRequest;
import dev.knalis.trajectaapi.dto.user.UserResponse;
import dev.knalis.trajectaapi.exception.RateLimitException;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.security.LoginRateLimiter;
import dev.knalis.trajectaapi.service.intrf.auth.JwtService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private LoginRateLimiter rateLimiter;
    @Mock
    private UserService userService;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authenticationManager, jwtService, userDetailsService, rateLimiter, userService, userMapper);
    }

    @Test
    void register_returnsTokenAndUser() {
        RegisterRequest request = new RegisterRequest();
        User user = new User();
        UserResponse response = new UserResponse();

        when(userService.register(request)).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt");
        when(userMapper.toDto(user)).thenReturn(response);

        var auth = service.register(request);

        assertThat(auth.getToken()).isEqualTo("jwt");
        assertThat(auth.getUser()).isSameAs(response);
    }

    @Test
    void login_throwsWhenRateLimited() {
        when(rateLimiter.isAllowed("u")).thenReturn(false);

        assertThatThrownBy(() -> service.login("u", "p")).isInstanceOf(RateLimitException.class);
    }

    @Test
    void login_recordsFailureAndRethrowsBadCredentials() {
        when(rateLimiter.isAllowed("u")).thenReturn(true);
        doThrow(new BadCredentialsException("x")).when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> service.login("u", "wrong"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");

        verify(rateLimiter).onFailure("u");
    }

    @Test
    void login_success_returnsTokenAndUser() {
        User details = new User();
        details.setId(5L);
        details.setUsername("pilot");
        UserResponse dto = new UserResponse();
        dto.setUsername("pilot");

        when(rateLimiter.isAllowed("pilot")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("pilot")).thenReturn(details);
        when(jwtService.generateToken(details)).thenReturn("jwt-token");
        when(userMapper.toDto(details)).thenReturn(dto);

        var response = service.login("pilot", "pass");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getUsername()).isEqualTo("pilot");
        verify(rateLimiter).onSuccess("pilot");
        verify(userService, never()).findByUsername(any());
    }

    @Test
    void login_allowsBannedUserAndReturnsToken() {
        User details = new User();
        details.setId(55L);
        details.setUsername("pilot");
        UserResponse dto = new UserResponse();
        dto.setUsername("pilot");

        when(rateLimiter.isAllowed("pilot")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("pilot")).thenReturn(details);
        when(jwtService.generateToken(details)).thenReturn("jwt-token");
        when(userMapper.toDto(details)).thenReturn(dto);

        var response = service.login("pilot", "pass");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getUsername()).isEqualTo("pilot");
        verify(rateLimiter).onSuccess("pilot");
    }
}

