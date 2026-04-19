package dev.knalis.trajectaapi.service.impl.cache;

import dev.knalis.trajectaapi.dto.admin.CacheClearResponse;
import dev.knalis.trajectaapi.dto.admin.CacheHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceHealthResponse;
import dev.knalis.trajectaapi.dto.admin.ServiceRuntimeMetricsResponse;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import dev.knalis.trajectaapi.repo.FlightTaskRepository;
import dev.knalis.trajectaapi.repo.UserRepository;
import dev.knalis.trajectaapi.service.intrf.user.CacheAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CacheAdminServiceImpl implements CacheAdminService {

    private final CacheManager cacheManager;
    private final UserRepository userRepository;
    private final RedisConnectionFactory redisConnectionFactory;
    private final FlightTaskRepository flightTaskRepository;

    @Override
    public CacheClearResponse clearUserCaches(Long userId) {
        if (userId == null) {
            return CacheClearResponse.builder()
                    .userId(null)
                    .userExists(false)
                    .usernameKeyEvicted(false)
                    .build();
        }

        var userOpt = userRepository.findById(userId);

        boolean usernameEvicted = false;
        if (userOpt.isPresent()) {
            String username = userOpt.get().getUsername();
            if (!username.isBlank()) {
                evict("notificationDtoByUser", username);
                usernameEvicted = true;
            }
        }

        return CacheClearResponse.builder()
                .userId(userId)
                .userExists(userOpt.isPresent())
                .usernameKeyEvicted(usernameEvicted)
                .build();
    }

    @Override
    public CacheHealthResponse cacheHealth() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if (pong == null || pong.isBlank()) {
                return CacheHealthResponse.builder()
                        .status("DOWN")
                        .redisPing("NONE")
                        .error("Redis ping returned empty response")
                        .build();
            }

            return CacheHealthResponse.builder()
                    .status("UP")
                    .redisPing(pong)
                    .error(null)
                    .build();
        } catch (Exception ex) {
            return CacheHealthResponse.builder()
                    .status("DOWN")
                    .redisPing("ERROR")
                    .error(ex.getMessage())
                    .build();
        }
    }

    @Override
    public ServiceHealthResponse serviceHealth() {
        String dbStatus = "UP";
        String dbError = null;
        try {
            userRepository.count();
        } catch (Exception ex) {
            dbStatus = "DOWN";
            dbError = ex.getMessage();
        }

        CacheHealthResponse cacheHealth = cacheHealth();
        String redisStatus = "UP".equalsIgnoreCase(cacheHealth.getStatus()) ? "UP" : "DOWN";
        String overall = "UP".equals(dbStatus) && "UP".equals(redisStatus) ? "UP" : "DEGRADED";

        return ServiceHealthResponse.builder()
                .status(overall)
                .database(dbStatus)
                .redis(redisStatus)
                .databaseError(dbError)
                .redisError(cacheHealth.getError())
                .checkedAt(Instant.now())
                .build();
    }

    @Override
    public ServiceRuntimeMetricsResponse runtimeMetrics() {
        ServiceHealthResponse serviceHealth = serviceHealth();
        Runtime runtime = Runtime.getRuntime();

        long heapUsedMb = bytesToMb(runtime.totalMemory() - runtime.freeMemory());
        long heapMaxMb = bytesToMb(runtime.maxMemory());

        long tasksTotal = flightTaskRepository.count();
        long tasksFailed = flightTaskRepository.countByStatus(TaskStatus.FAILED);
        double failureRate = tasksTotal > 0 ? (tasksFailed * 100.0) / tasksTotal : 0.0;
        String stability = computeStability(serviceHealth.getStatus(), heapUsedMb, heapMaxMb, failureRate);

        return ServiceRuntimeMetricsResponse.builder()
                .status(serviceHealth.getStatus())
                .stability(stability)
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000L)
                .activeThreads(ManagementFactory.getThreadMXBean().getThreadCount())
                .heapUsedMb(heapUsedMb)
                .heapMaxMb(heapMaxMb)
                .usersTotal(userRepository.count())
                .tasksTotal(tasksTotal)
                .tasksFailed(tasksFailed)
                .taskFailureRatePct(Math.round(failureRate * 10.0) / 10.0)
                .checkedAt(Instant.now())
                .build();
    }

    private static long bytesToMb(long value) {
        return value / (1024L * 1024L);
    }

    private static String computeStability(String status, long heapUsedMb, long heapMaxMb, double failureRate) {
        if (!"UP".equalsIgnoreCase(status)) {
            return "UNSTABLE";
        }

        double heapPressure = heapMaxMb > 0 ? (heapUsedMb * 100.0) / heapMaxMb : 0.0;
        if (heapPressure >= 90.0 || failureRate >= 20.0) {
            return "UNSTABLE";
        }
        if (heapPressure >= 75.0 || failureRate >= 10.0) {
            return "RISKY";
        }
        return "STABLE";
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null) {
            cache.evict(key);
        }
    }
}

