package com.gradpilot.health;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A lightweight endpoint the frontend and deployment checks can call
 * to confirm the API is running.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok", "gradpilot-backend", Instant.now());
    }

    public record HealthResponse(String status, String service, Instant time) {
    }
}
