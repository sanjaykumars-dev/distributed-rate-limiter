package com.sanjay.ratelimiter.controller;

import com.sanjay.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LimitController {

    private final RateLimiterService rateLimiterService;

    @GetMapping("/limit")
    public ResponseEntity<String> checkLimit(@RequestParam String userId, @RequestParam String endpoint){
        if(! rateLimiterService.isAllowed(userId,endpoint)){
            return ResponseEntity
                    .status(429)
                    .header("X-Rate-Limit","5") //TODO : make configurable per user or per IP
                    .header("X-Window-Duration","1") //TODO : make configurable per user or per IP
                    .body("Too many request for " + endpoint);
        }

        return ResponseEntity.status(200).body("Allowed");
    }
}
