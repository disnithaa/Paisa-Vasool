package com.paisavasool.app.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "app.dev-auth.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paisa_vasool")
            .withUsername("paisa")
            .withPassword("integration-test-password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void flywayAppliesAllMigrationsAndSeedsTheOfferCatalog() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT MAX(version) FROM app.flyway_schema_history WHERE success = TRUE",
            String.class
        )).isEqualTo("6");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app.offers WHERE is_sample = TRUE",
            Integer.class
        )).isEqualTo(2);
    }

    @Test
    void monetaryColumnsRetainTheirPostgresPrecision() {
        assertThat(jdbcTemplate.queryForObject(
            """
                SELECT numeric_precision
                FROM information_schema.columns
                WHERE table_schema = 'app'
                  AND table_name = 'offers'
                  AND column_name = 'discount_amount'
                """,
            Integer.class
        )).isEqualTo(12);

        assertThat(jdbcTemplate.queryForObject(
            """
                SELECT numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'app'
                  AND table_name = 'offers'
                  AND column_name = 'discount_amount'
                """,
            Integer.class
        )).isEqualTo(2);
    }
}
