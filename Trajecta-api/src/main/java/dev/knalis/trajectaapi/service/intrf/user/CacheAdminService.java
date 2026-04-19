package dev.knalis.trajectaapi.service.intrf.user;

import dev.knalis.trajectaapi.dto.admin.CacheClearResponse;
import dev.knalis.trajectaapi.dto.admin.CacheHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceRuntimeMetricsResponse;

public interface CacheAdminService {
    CacheClearResponse clearUserCaches(Long userId);

    CacheHealthResponse cacheHealth();

    ServiceHealthResponse serviceHealth();

    ServiceRuntimeMetricsResponse runtimeMetrics();
}
