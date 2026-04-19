package dev.knalis.trajectaapi.security;

import dev.knalis.trajectaapi.config.props.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TaskCreationRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    public boolean isAllowed(Long userId) {
        if (userId == null) {
            return false;
        }

        String value = redisTemplate.opsForValue().get(redisKey(userId));
        if (value == null) {
            return true;
        }

        int attempts;
        try {
            attempts = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            redisTemplate.delete(redisKey(userId));
            return true;
        }

        return attempts < rateLimitProperties.getTaskCreate().getMaxAttempts();
    }

    public void onAttempt(Long userId) {
        if (userId == null) {
            return;
        }

        Long attempts = redisTemplate.opsForValue().increment(redisKey(userId));
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(redisKey(userId), Duration.ofSeconds(rateLimitProperties.getTaskCreate().getWindowSeconds()));
        }
    }

    private String redisKey(Long userId) {
        return rateLimitProperties.getTaskCreate().getKeyPrefix() + userId;
    }
}



