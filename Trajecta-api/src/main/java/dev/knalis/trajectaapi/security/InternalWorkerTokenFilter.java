package dev.knalis.trajectaapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalWorkerTokenFilter extends OncePerRequestFilter {

    private static final String WORKER_TOKEN_HEADER = "X-Worker-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    @Value("${application.internal.worker-token}")
    private String workerToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            path = request.getServletPath();
        }
        return path == null || !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(WORKER_TOKEN_HEADER);

        if (!isValidToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid worker token");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal-worker",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_WORKER"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private boolean isValidToken(String token) {
        if (token == null || token.isBlank() || workerToken == null || workerToken.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                workerToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
    }
}



