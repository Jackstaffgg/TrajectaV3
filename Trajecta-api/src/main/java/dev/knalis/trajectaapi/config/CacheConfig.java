package dev.knalis.trajectaapi.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder().build()))
                .prefixCacheNameWith("v2::")
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        RedisCacheConfiguration taskDtoConfig = defaultConfig.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new JdkSerializationRedisSerializer(getClass().getClassLoader())
                )
        );

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("notificationDtoByUser", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("taskRawKey", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("taskTrajectoryKey", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("taskDtoByIdAndUserV3", taskDtoConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigs.put("taskDtoByUserPageV3", taskDtoConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put("userByIdV1", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("userByUsernameV1", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("userPageV1", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigs.put("punishmentBanFlagByUserV1", defaultConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
