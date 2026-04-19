package dev.knalis.trajectaapi.security;

import dev.knalis.trajectaapi.config.props.RequestLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String TASK_CREATE_PATH = "/api/v1/tasks";
    private static final long UNAUTHORIZED_WINDOW_MS = 10_000;
    private static final int UNAUTHORIZED_MAX_ATTEMPTS = 20;
    private static final int UNAUTHORIZED_TRACKING_MAX_KEYS = 10_000;
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "token", "password", "secret", "key", "authorization", "jwt", "api_key"
    );

    private final RequestLoggingProperties properties;
    private final Map<String, AuthFailureWindow> unauthorizedTaskCreateByClient = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (properties.getExcludePathPrefixes() == null) {
            return false;
        }
        for (String prefix : properties.getExcludePathPrefixes()) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        if (isTaskCreateRequest(request) && shouldThrottleUnauthorizedTaskCreate(request, startedAt)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "10");
            log.warn("http method={} path={} status={} durationMs={} requestId={} reason=unauthorized_task_create_throttle",
                    request.getMethod(), safePath(request), HttpStatus.TOO_MANY_REQUESTS.value(), 0, requestId);
            return;
        }

        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            recordUnauthorizedTaskCreate(request, response, startedAt);
            logRequest(request, response, durationMs, requestId);
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    private boolean isTaskCreateRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && TASK_CREATE_PATH.equals(request.getRequestURI());
    }

    private boolean shouldThrottleUnauthorizedTaskCreate(HttpServletRequest request, long nowMs) {
        String clientKey = clientKey(request);
        AuthFailureWindow state = unauthorizedTaskCreateByClient.get(clientKey);
        if (state == null) {
            return false;
        }

        if (nowMs - state.windowStartedAtMs > UNAUTHORIZED_WINDOW_MS) {
            unauthorizedTaskCreateByClient.remove(clientKey, state);
            return false;
        }

        return state.attempts.get() >= UNAUTHORIZED_MAX_ATTEMPTS;
    }

    private void recordUnauthorizedTaskCreate(HttpServletRequest request, HttpServletResponse response, long nowMs) {
        if (!isTaskCreateRequest(request)) {
            return;
        }

        if (response.getStatus() != HttpStatus.UNAUTHORIZED.value()) {
            return;
        }

        if (unauthorizedTaskCreateByClient.size() > UNAUTHORIZED_TRACKING_MAX_KEYS) {
            cleanupExpiredTrackingEntries(nowMs);
        }

        String clientKey = clientKey(request);
        unauthorizedTaskCreateByClient.compute(clientKey, (key, existing) -> {
            if (existing == null || nowMs - existing.windowStartedAtMs > UNAUTHORIZED_WINDOW_MS) {
                return new AuthFailureWindow(nowMs, 1);
            }
            existing.attempts.incrementAndGet();
            return existing;
        });
    }

    private void cleanupExpiredTrackingEntries(long nowMs) {
        unauthorizedTaskCreateByClient.entrySet().removeIf(
                entry -> nowMs - entry.getValue().windowStartedAtMs > UNAUTHORIZED_WINDOW_MS
        );
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int idx = forwardedFor.indexOf(',');
            String ip = idx >= 0 ? forwardedFor.substring(0, idx) : forwardedFor;
            String normalized = ip.trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return request.getRemoteAddr();
    }

    private static final class AuthFailureWindow {
        private final long windowStartedAtMs;
        private final AtomicInteger attempts;

        private AuthFailureWindow(long windowStartedAtMs, int attempts) {
            this.windowStartedAtMs = windowStartedAtMs;
            this.attempts = new AtomicInteger(attempts);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs, String requestId) {
        String pattern = "http method={} path={} status={} durationMs={} requestId={}";
        String method = request.getMethod();
        String path = safePath(request);
        int status = response.getStatus();

        if (status >= 500) {
            log.warn(pattern, method, path, status, durationMs, requestId);
            return;
        }

        if (status >= 400 || durationMs >= properties.getSlowThresholdMs()) {
            log.info(pattern, method, path, status, durationMs, requestId);
            return;
        }

        switch (properties.getSuccessLogLevel()) {
            case INFO -> log.info(pattern, method, path, status, durationMs, requestId);
            case OFF -> {
                return;
            }
            default -> log.debug(pattern, method, path, status, durationMs, requestId);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = incoming.trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String safePath(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return request.getRequestURI();
        }

        String[] pairs = query.split("&");
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";

            if (i > 0) {
                masked.append('&');
            }

            if (isSensitiveKey(key)) {
                masked.append(key).append("=***");
            } else {
                masked.append(key);
                if (idx >= 0) {
                    masked.append('=').append(value);
                }
            }
        }

        return request.getRequestURI() + "?" + masked;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase();
        for (String sensitive : SENSITIVE_QUERY_KEYS) {
            if (normalized.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}





