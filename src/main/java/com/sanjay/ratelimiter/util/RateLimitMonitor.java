package com.sanjay.ratelimiter.util;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class RateLimitMonitor {

    private final Counter totalRequests = Counter.builder("rate_limiter_requests_total")
            .description("Total number of requests processed by rate limiter")
            .register(Metrics.globalRegistry);

    private final Counter totalRequestsDropped = Counter.builder("rate_limiter_requests_total_dropped")
            .description("Total number of requests processed by rate limiter")
            .register(Metrics.globalRegistry);
}
