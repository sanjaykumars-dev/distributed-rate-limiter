package com.sanjay.ratelimiter.util;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "rate-limiter")
@Data
public class RateLimiterProperties {

    private Map<String,RateLimitConfig> endpoints = new HashMap<>();
    private RateLimitConfig defaultConfig = new RateLimitConfig();
    private RateLimitConfig global = new RateLimitConfig();

    @Data
    public static class RateLimitConfig{
        private long windowDuration = 60;
        private long requestLimit = 5;
    }

    public RateLimitConfig getConfigFor(String endpoint){
        return endpoints.getOrDefault(endpoint,defaultConfig);
    }
}
