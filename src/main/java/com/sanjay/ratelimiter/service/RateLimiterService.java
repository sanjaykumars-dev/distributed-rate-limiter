package com.sanjay.ratelimiter.service;

import com.sanjay.ratelimiter.util.RateLimitMonitor;
import com.sanjay.ratelimiter.util.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;

/**
 * Core service that performs rate limiting using Redis and Lua scripting.
 *
 * <p>This service enforces three levels of rate limits:
 * <ul>
 *   <li><b>Global limit</b> — total requests allowed system-wide across all users.</li>
 *   <li><b>Per-endpoint limit</b> — requests allowed for a specific API endpoint.</li>
 *   <li><b>Per-user limit</b> — requests allowed per user per endpoint.</li>
 * </ul>
 *
 * <p>All rate limit logic is performed atomically inside Redis using a Lua script,
 * ensuring consistency even under high concurrency.
 *
 * <p>Rate limiter configuration (limits and window durations) is dynamically
 * loaded from {@link RateLimiterProperties}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    /** Monitor component (optional) for metrics, logging, and future alerting integration */
    private final RateLimitMonitor monitor;

    /** Centralized configuration that defines global, endpoint, and user-specific limits */
    private final RateLimiterProperties properties;

    /** Redis template for executing Lua scripts atomically */
    private final StringRedisTemplate redisTemplate;

    /** Cached Redis script for atomic operations */
    private DefaultRedisScript<Long> script;

    /**
     * Loads the Lua script that performs rate limiting logic in Redis.
     *
     * <p>The script is loaded once and cached for subsequent executions.
     * It uses Redis sorted sets (ZSET) to store timestamps of requests,
     * and performs operations atomically to:
     * <ul>
     *   <li>Remove expired entries (outside the current window)</li>
     *   <li>Count current requests</li>
     *   <li>Add a new timestamp if within the allowed limit</li>
     * </ul>
     *
     * @return a {@link DefaultRedisScript} representing the compiled Lua script.
     */
    private DefaultRedisScript<Long> getScript() {
        if (script == null) {
            try {
                // Load the Lua script from classpath once during runtime
                String lua = Files.readString(
                        new ClassPathResource("script/RateLimiterScript.lua")
                                .getFile().toPath());
                script = new DefaultRedisScript<>(lua, Long.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Lua script", e);
            }
        }
        return script;
    }

    /**
     * Checks if a request from a user to a specific endpoint is allowed.
     *
     * <p>This method performs three independent rate-limit checks:
     * <ol>
     *   <li><b>Global Limit:</b> Ensures total system requests are below threshold.</li>
     *   <li><b>Endpoint Limit:</b> Ensures this specific API is not overloaded.</li>
     *   <li><b>User Limit:</b> Ensures individual users stay within fair usage bounds.</li>
     * </ol>
     *
     * <p>The request is allowed only if all three checks pass.
     *
     * @param userId   unique identifier of the user making the request
     * @param endpoint API endpoint being accessed (e.g., "/login", "/data")
     * @return {@code true} if the request is allowed, {@code false} otherwise
     */
    public boolean isAllowed(String userId, String endpoint) {
        long now = Instant.now().getEpochSecond(); // current timestamp in seconds

        //Check global limit — applies system-wide to all requests
        var globalConfig = properties.getGlobal();
        boolean isGlobalAllowed = checkLimit("rate_limit:global", now, globalConfig);

        //Check endpoint-specific limit
        var endPointConfig = properties.getConfigFor(endpoint);
        boolean isEndPointAllowed = checkLimit("rate_limit:endpoint:" + endpoint, now, endPointConfig);

        //Check per-user limit (user + endpoint combination)
        var userKey = "rate_limit:user:" + userId + ":" + endpoint;
        boolean isUserAllowed = checkLimit(userKey, now, endPointConfig);

        // Logging decisions for observability and debugging
        if (!isGlobalAllowed) log.warn("Global limit reached!");
        if (!isEndPointAllowed) log.warn("Endpoint limit reached: {}", endpoint);
        if (!isUserAllowed) log.warn("User {} exceeded limit for {}", userId, endpoint);

        // The request is allowed only if all checks passed
        return isGlobalAllowed && isEndPointAllowed && isUserAllowed;
    }

    /**
     * Executes the Lua rate limiting script for a specific Redis key.
     *
     * <p>The key can represent any limit scope (global, endpoint, or user).
     * The script atomically:
     * <ul>
     *   <li>Removes old request timestamps outside the defined time window</li>
     *   <li>Counts current requests in the window</li>
     *   <li>Allows or blocks the new request based on the configured limit</li>
     * </ul>
     *
     * @param key     Redis key representing the limiter (e.g. "rate_limit:user:sanjay:/login")
     * @param now     current timestamp in seconds
     * @param config  configuration defining limit and window duration
     * @return {@code true} if allowed, {@code false} if limit exceeded
     */
    private boolean checkLimit(String key, long now, RateLimiterProperties.RateLimitConfig config) {
        Long result = redisTemplate.execute(
                getScript(),
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(config.getWindowDuration()),
                String.valueOf(config.getRequestLimit())
        );

        // Redis Lua script returns 1 (allowed) or 0 (denied)
        return result != null && result == 1L;
    }
}
