package com.ecommerce.cartservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the V2__Add_perf_indexes Flyway migration created the
 * performance index documented in DB_INDEX_AUDIT.md. Runs against real
 * Postgres via Testcontainers so we exercise the same DDL path production uses.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartIndexAuditTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("cartdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("spring.flyway.locations",    () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_haveIdxCartStatusUpdated_after_runningV2Migration() {
        List<String> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'carts'",
            String.class
        );

        assertThat(indexes)
            .as("V2 must add idx_cart_status_updated for findAbandonedCarts")
            .contains("idx_cart_status_updated");
    }

    @Test
    void should_keepLegacyIndexes_after_runningV2Migration() {
        // Append-only: V2 must NOT drop or rename existing indexes.
        List<String> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'carts'",
            String.class
        );

        assertThat(indexes).contains(
            "idx_user_id",
            "idx_status",
            "idx_expires_at"
        );
    }
}
