package dev.knalis.trajectaapi.service.impl.auth;

import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

    private JwtServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JwtServiceImpl();
        ReflectionTestUtils.setField(service, "secret", "0123456789012345678901234567890101234567890123456789012345678901");
        ReflectionTestUtils.setField(service, "expiration", 60000L);
    }

    @Test
    void generateAndExtractUsername() {
        User user = new User();
        user.setUsername("pilot");
        user.setRole(Role.ADMIN);

        String token = service.generateToken(user);

        assertThat(service.extractUsername(token)).isEqualTo("pilot");
        assertThat(service.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenForGenericUserDetails_isStillValidForSamePrincipal() {
        var springUser = org.springframework.security.core.userdetails.User
                .withUsername("guest")
                .password("x")
                .roles("USER")
                .build();

        String token = service.generateToken(springUser);

        assertThat(service.extractUsername(token)).isEqualTo("guest");
        assertThat(service.isTokenValid(token, springUser)).isTrue();
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        User owner = new User();
        owner.setUsername("owner");
        owner.setRole(Role.USER);

        var another = org.springframework.security.core.userdetails.User
                .withUsername("another")
                .password("x")
                .roles("USER")
                .build();

        String token = service.generateToken(owner);

        assertThat(service.isTokenValid(token, another)).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtServiceImpl shortLived = new JwtServiceImpl();
        ReflectionTestUtils.setField(shortLived, "secret", "0123456789012345678901234567890101234567890123456789012345678901");
        ReflectionTestUtils.setField(shortLived, "expiration", -1L);

        User user = new User();
        user.setUsername("pilot");
        user.setRole(Role.USER);

        String token = shortLived.generateToken(user);

        assertThatThrownBy(() -> shortLived.isTokenValid(token, user))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}

