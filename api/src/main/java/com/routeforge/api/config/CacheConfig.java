package com.routeforge.api.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory cache backed by {@link java.util.concurrent.ConcurrentHashMap}.
 * <p>
 * Good enough for Phase 3: a single API instance keeps recent
 * (from, to, profile, algo) → result mappings in memory. Identical
 * repeated requests are served in microseconds.
 * <p>
 * Phase 3.5 swap to Redis is one bean change: replace this with a
 * {@code RedisCacheManager} and the service annotations are unchanged.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("routes");
    }
}
