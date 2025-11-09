package com.sanjay.ratelimiter.controller;

import com.sanjay.ratelimiter.util.RateLimiterProperties;
import com.sanjay.ratelimiter.util.RateLimiterProperties.RateLimitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Admin controller for dynamically updating rate limiter configurations at runtime.
 *
 * This allows real-time tuning of rate limit parameters (window duration & request limit)
 * for global, default, or endpoint-specific settings — without restarting the application.
 *
 * Examples:
 *   - Update global limit:
 *       POST /api/admin/ratelimiter/update?type=global&window=120&limit=10000
 *   - Update default (per-endpoint fallback):
 *       POST /api/admin/ratelimiter/update?type=default&window=60&limit=5
 *   - Update specific endpoint:
 *       POST /api/admin/ratelimiter/update?type=endpoint&name=/login&window=30&limit=3
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ratelimiter")
public class RateLimiterConfigController {

    // Injects the central configuration bean that holds all rate limit settings
    private final RateLimiterProperties properties;

    /**
     * Updates rate limiter configuration dynamically.
     *
     * @param type   Configuration scope: "global", "default", or "endpoint"
     * @param name   Endpoint name (required only if type=endpoint)
     * @param window New window duration in seconds (optional)
     * @param limit  New request limit (optional)
     * @return HTTP response indicating update status
     */
    @PostMapping("/update")
    public ResponseEntity<String> updateConfig(
            @RequestParam String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "0") long window,
            @RequestParam(required = false, defaultValue = "0") long limit) {

        RateLimitConfig configToUpdate;

        // Select which configuration to update based on the 'type' parameter
        switch (type.toLowerCase()) {

            //Global configuration: system-wide limit for all traffic
            case "global" -> configToUpdate = properties.getGlobal();

            //Default configuration: fallback for endpoints not explicitly defined
            case "default" -> configToUpdate = properties.getDefaultConfig();

            //Endpoint-specific configuration
            case "endpoint" -> {
                // Require endpoint name (e.g., "/login" or "/data")
                if (name == null || name.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body("'name' parameter required for endpoint type");
                }

                // Retrieve existing config or create a new one if it doesn't exist
                configToUpdate = Optional.ofNullable(properties.getEndpoints().get(name))
                        .orElseGet(() -> {
                            RateLimitConfig newCfg = new RateLimitConfig();
                            properties.getEndpoints().put(name, newCfg);
                            return newCfg;
                        });
            }

            // Invalid type -> return bad request
            default -> {
                return ResponseEntity.badRequest()
                        .body("Invalid type. Must be one of: global, default, endpoint");
            }
        }

        // Apply new values only if they are positive (0 means "no change")
        if (window > 0L) configToUpdate.setWindowDuration(window);
        if (limit > 0L) configToUpdate.setRequestLimit(limit);

        // Construct readable response message
        String updated = String.format(
                "Updated %s config%s → window=%d, limit=%d",
                type.toUpperCase(),
                (name != null ? " (" + name + ")" : ""),
                configToUpdate.getWindowDuration(),
                configToUpdate.getRequestLimit()
        );

        // Return confirmation message
        return ResponseEntity.status(HttpStatus.OK).body(updated);
    }
}
