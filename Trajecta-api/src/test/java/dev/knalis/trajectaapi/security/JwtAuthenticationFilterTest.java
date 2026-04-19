package dev.knalis.trajectaapi.security;

import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.service.impl.cache.PunishmentCacheService;
import dev.knalis.trajectaapi.service.intrf.auth.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private PunishmentCacheService punishmentCacheService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                punishmentCacheService,
                JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void bannedUserReceivesForbiddenWithUserBannedCode() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("pilot");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("token-1")).thenReturn("pilot");
        when(userDetailsService.loadUserByUsername("pilot")).thenReturn(user);
        when(jwtService.isTokenValid("token-1", user)).thenReturn(true);
        when(punishmentCacheService.isUserBanned(42L)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"USER_BANNED\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"User is banned\"");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void banStatusEndpointIsAllowedForBannedUser() throws Exception {
        User user = new User();
        user.setId(43L);
        user.setUsername("pilot");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me/ban-status");
        request.addHeader("Authorization", "Bearer token-2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("token-2")).thenReturn("pilot");
        when(userDetailsService.loadUserByUsername("pilot")).thenReturn(user);
        when(jwtService.isTokenValid("token-2", user)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(punishmentCacheService, never()).isUserBanned(any(Long.class));
    }
}
