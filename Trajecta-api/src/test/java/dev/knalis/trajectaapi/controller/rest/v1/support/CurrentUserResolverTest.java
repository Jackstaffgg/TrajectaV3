package dev.knalis.trajectaapi.controller.rest.v1.support;

import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserResolverTest {

    private final CurrentUserResolver resolver = new CurrentUserResolver();

    @Test
    void requireUser_returnsPrincipal() {
        Authentication auth = mock(Authentication.class);
        User user = new User();
        when(auth.getPrincipal()).thenReturn(user);

        assertThat(resolver.requireUser(auth)).isSameAs(user);
    }

    @Test
    void requireUser_throwsForInvalidPrincipal() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-user");

        assertThatThrownBy(() -> resolver.requireUser(auth)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void requireUser_throwsForNullAuthentication() {
        assertThatThrownBy(() -> resolver.requireUser(null)).isInstanceOf(PermissionDeniedException.class);
    }
}

