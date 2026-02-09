package com.jobmonitor.platform.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;

/**
 * Centralized Redis/Cache configuration.
 * <p>
 * All TTLs come from {@link PlatformProperties.CacheConfig} — zero hardcoded.
 * Named caches get individual TTLs; anything else gets the default.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final PlatformProperties properties;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var cacheConfig = properties.getCache();

        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheConfig.getDefaultTtl())
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Build per-cache TTL configs
        var cacheConfigs = new HashMap<String, RedisCacheConfiguration>();
        cacheConfig.getTtls().forEach((cacheName, ttl) -> {
            cacheConfigs.put(cacheName, defaultConfig.entryTtl(ttl));
            log.info("Cache '{}' TTL configured: {}", cacheName, ttl);
        });

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
