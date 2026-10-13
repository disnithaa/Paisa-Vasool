package com.paisavasool.app.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HealthControllerTest {

    @Test
    void healthEndpointReturnsServiceAndDatabaseStatus() {
        var controller = new HealthController(new StubDatabaseHealthChecker());

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("service", "paisa-vasool-backend");
        assertThat(response.getBody()).containsKey("database");
    }

    private static final class StubDatabaseHealthChecker extends DatabaseHealthChecker {
        private StubDatabaseHealthChecker() {
            super(null);
        }

        @Override
        public Map<String, Object> getDatabaseStatus() {
            return Map.of(
                "status", "UP",
                "database", "postgresql",
                "healthy", true
            );
        }
    }
}
