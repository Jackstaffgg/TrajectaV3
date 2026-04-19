package dev.knalis.trajectaapi.controller.rest.v1.auth;

import dev.knalis.trajectaapi.dto.auth.AuthRequest;
import dev.knalis.trajectaapi.dto.auth.AuthResponse;
import dev.knalis.trajectaapi.dto.auth.RegisterRequest;
import dev.knalis.trajectaapi.service.intrf.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void register_returnsCreated() {
        RegisterRequest request = new RegisterRequest();
        when(authService.register(request)).thenReturn(new AuthResponse("t", null));

        var response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void login_returnsOk() {
        AuthRequest request = new AuthRequest();
        request.setUsername("u");
        request.setPassword("p");
        when(authService.login("u", "p")).thenReturn(new AuthResponse("t", null));

        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getToken()).isEqualTo("t");
    }
}

