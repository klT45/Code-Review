package com.codereview.assistant.api;

import com.codereview.assistant.config.AppProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AppProperties appProperties;

    public HealthController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", appProperties.name(), Instant.now());
    }

    public record HealthResponse(String status, String service, Instant checkedAt) {
    }
}
