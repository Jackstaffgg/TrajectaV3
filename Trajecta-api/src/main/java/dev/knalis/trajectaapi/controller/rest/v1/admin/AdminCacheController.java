package dev.knalis.trajectaapi.controller.rest.v1.admin;

import dev.knalis.trajectaapi.dto.admin.CacheClearResponse;
import dev.knalis.trajectaapi.dto.admin.CacheHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceRuntimeMetricsResponse;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.service.intrf.user.CacheAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/cache")
@RequiredArgsConstructor
@Tag(name = "Admin Cache", description = "Administrative cache maintenance endpoints.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCacheController {

    private final CacheAdminService cacheAdminService;

    @Operation(summary = "Clear user caches", description = "Evicts user-related cache entries and refreshes user list/search caches.")
    @PostMapping("/users/{userId}/clear")
    public ResponseEntity<ApiResponse<CacheClearResponse>> clearUserCaches(
            @Parameter(description = "User identifier", example = "42") @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(cacheAdminService.clearUserCaches(userId)));
    }

    @Operation(summary = "Cache health", description = "Returns Redis ping status for cache infrastructure.")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<CacheHealthResponse>> cacheHealth() {
        return ResponseEntity.ok(ApiResponse.success(cacheAdminService.cacheHealth()));
    }

    @Operation(summary = "Service health", description = "Returns aggregated backend service status (database + redis).")
    @GetMapping("/service-health")
    public ResponseEntity<ApiResponse<ServiceHealthResponse>> serviceHealth() {
        return ResponseEntity.ok(ApiResponse.success(cacheAdminService.serviceHealth()));
    }

    @Operation(summary = "Runtime metrics", description = "Returns runtime metrics, statistics and computed stability indicator.")
    @GetMapping("/runtime-metrics")
    public ResponseEntity<ApiResponse<ServiceRuntimeMetricsResponse>> runtimeMetrics() {
        return ResponseEntity.ok(ApiResponse.success(cacheAdminService.runtimeMetrics()));
    }
}
