package dev.knalis.trajectaapi.controller.rest.v1.admin;

import dev.knalis.trajectaapi.dto.admin.CacheClearResponse;
import dev.knalis.trajectaapi.dto.admin.CacheHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceHealthResponse;
import dev.knalis.trajectaapi.service.intrf.user.CacheAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCacheControllerTest {

    @Mock
    private CacheAdminService cacheAdminService;

    private AdminCacheController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminCacheController(cacheAdminService);
    }

    @Test
    void clearUserCaches_returnsServiceResponse() {
        CacheClearResponse payload = CacheClearResponse.builder()
                .userId(7L)
                .userExists(true)
                .usernameKeyEvicted(true)
                .build();

        when(cacheAdminService.clearUserCaches(7L)).thenReturn(payload);

        var response = controller.clearUserCaches(7L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getUserId()).isEqualTo(7L);
        verify(cacheAdminService).clearUserCaches(7L);
    }

    @Test
    void cacheHealth_returnsServiceResponse() {
        CacheHealthResponse payload = CacheHealthResponse.builder()
                .status("UP")
                .redisPing("PONG")
                .error(null)
                .build();

        when(cacheAdminService.cacheHealth()).thenReturn(payload);

        var response = controller.cacheHealth();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getStatus()).isEqualTo("UP");
        verify(cacheAdminService).cacheHealth();
    }

    @Test
    void serviceHealth_returnsServiceResponse() {
        ServiceHealthResponse payload = ServiceHealthResponse.builder()
                .status("UP")
                .database("UP")
                .redis("UP")
                .databaseError(null)
                .redisError(null)
                .build();

        when(cacheAdminService.serviceHealth()).thenReturn(payload);

        var response = controller.serviceHealth();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getStatus()).isEqualTo("UP");
        verify(cacheAdminService).serviceHealth();
    }
    
}
