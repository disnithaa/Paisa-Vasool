package com.paisavasool.app.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DatabaseHealthChecker databaseHealthChecker;

    public HealthController(DatabaseHealthChecker databaseHealthChecker) {
        this.databaseHealthChecker = databaseHealthChecker;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> database = databaseHealthChecker.getDatabaseStatus();
        boolean healthy = Boolean.TRUE.equals(database.get("healthy"));
        response.put("status", healthy ? "UP" : "DOWN");
        response.put("service", "paisa-vasool-backend");
        response.put("timestamp", Instant.now().toString());
        response.put("database", database);
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
