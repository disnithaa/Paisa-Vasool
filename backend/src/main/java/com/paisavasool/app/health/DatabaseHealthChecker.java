package com.paisavasool.app.health;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthChecker {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getDatabaseStatus() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean healthy = result != null && result == 1;
            return Map.of(
                "status", healthy ? "UP" : "DOWN",
                "database", "postgresql",
                "check", healthy ? "SELECT 1" : "failed",
                "healthy", healthy
            );
        } catch (Exception ex) {
            return Map.of(
                "status", "DOWN",
                "database", "postgresql",
                "check", "SELECT 1",
                "healthy", false
            );
        }
    }
}
