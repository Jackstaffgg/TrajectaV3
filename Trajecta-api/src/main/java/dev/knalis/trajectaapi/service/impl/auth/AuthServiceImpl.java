package dev.knalis.trajectaapi.service.impl.auth;

import dev.knalis.trajectaapi.dto.auth.AuthResponse;
import dev.knalis.trajectaapi.dto.auth.RegisterRequest;
import dev.knalis.trajectaapi.exception.RateLimitException;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.security.LoginRateLimiter;
import dev.knalis.trajectaapi.service.intrf.auth.AuthService;
import dev.knalis.trajectaapi.service.intrf.auth.JwtService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final LoginRateLimiter rateLimiter;
    
    private final UserService userService;
    private final UserMapper userMapper;
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    
    public AuthResponse register(RegisterRequest request) {
        final var user = userService.register(request);
        final var token = jwtService.generateToken(user);
        incrementCounter("auth.register.success");
        return new AuthResponse(token, userMapper.toDto(user));
    }
    
    public AuthResponse login(String username, String password) {
        if (!rateLimiter.isAllowed(username)) {
            incrementCounter("auth.login.rateLimited");
            throw new RateLimitException("Too many failed login attempts. Please try again later.");
        }
        
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (BadCredentialsException e) {
            rateLimiter.onFailure(username);
            incrementCounter("auth.login.failure");
            throw new BadCredentialsException("Invalid username or password");
        }
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        final var token = jwtService.generateToken(userDetails);

        User responseUser;
        if (userDetails instanceof User domainUser) {
            responseUser = domainUser;
        } else {

            responseUser = userService.findByUsername(username);
        }


        rateLimiter.onSuccess(username);
        incrementCounter("auth.login.success");

        return new AuthResponse(token, userMapper.toDto(responseUser));
    }

    private void incrementCounter(String name) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(name).increment();
    }
}


