package dev.knalis.trajectaapi.security;

import dev.knalis.trajectaapi.config.props.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    public boolean isAllowed(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String value = redisTemplate.opsForValue().get(redisKey(key));
        if (value == null) {
            return true;
        }

        int attempts;
        try {
            attempts = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            redisTemplate.delete(redisKey(key));
            return true;
        }

        return attempts < rateLimitProperties.getLogin().getMaxAttempts();
    }

    public void onFailure(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        Long attempts = redisTemplate.opsForValue().increment(redisKey(key));
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(redisKey(key), Duration.ofSeconds(rateLimitProperties.getLogin().getWindowSeconds()));
        }
    }

    public void onSuccess(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        redisTemplate.delete(redisKey(key));
    }

    private String redisKey(String key) {
        return rateLimitProperties.getLogin().getKeyPrefix() + key.toLowerCase();
    }
}



