package com.ecommerce.orderservice.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-level integration test for {@link OrderArchivalScheduler} that runs the
 * <em>real</em> archival {@code INSERT … SELECT} / {@code DELETE} SQL against a
 * schema built from the committed Flyway DDL (V1_1 → {@code orders}, V4 →
 * {@code orders_archive}) on an in-memory H2 in PostgreSQL-compatibility mode.
 *
 * <p>The pure-mock {@link OrderArchivalSchedulerTest} stubs {@link JdbcTemplate}
 * and therefore never touches a real schema — so it could not catch the column
 * mismatch this test targets: {@code orders} maps {@code Address.postalCode} to
 * {@code postal_code}, while the archive DDL and the scheduler SQL used
 * {@code zip_code}. That mismatch meant the archival job could never execute a
 * single batch (the {@code SELECT o.zip_code FROM orders} alone fails).
 *
 * <p>The schema build mirrors production: it loads the actual V1_1 and V4
 * migration resources, then applies the fix migration's effect (V10 renames
 * {@code orders_archive.zip_code} → {@code postal_code}) using H2 dialect — V10
 * itself is Postgres-only (PL/pgSQL {@code DO} block), consistent with the test
 * profile note that the Flyway scripts are Postgres-specific.
 */
@DisplayName("OrderArchivalScheduler — archival SQL against the Flyway-built schema")
class OrderArchivalSchemaIntegrationTest {

    private static final int CUTOFF_DAYS = 365;
    private static final int BATCH_SIZE = 1000;
    private static final Instant FIXED_NOW = LocalDateTime.of(2026, 4, 29, 4, 0)
            .toInstant(ZoneOffset.UTC);

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private OrderArchivalScheduler scheduler;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:archival-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        dataSource = new DriverManagerDataSource(url, "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);

        buildSchemaFromMigrations();

        scheduler = new OrderArchivalScheduler(
                jdbcTemplate,
                new SimpleMeterRegistry(),
                Clock.fixed(FIXED_NOW, ZoneId.of("UTC")),
                CUTOFF_DAYS,
                BATCH_SIZE,
                /* enabled */ true);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP ALL OBJECTS");
    }

    /**
     * Loads the real committed migration DDL, then applies the fix migration's
     * rename in H2 dialect (production V10 uses a Postgres {@code DO} block).
     */
    private void buildSchemaFromMigrations() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(
                "db/migration/V1_1__Create_core_order_tables.sql"));
        populator.addScript(new ClassPathResource(
                "db/migration/V4__Create_orders_archive.sql"));
        populator.execute(dataSource);

        jdbcTemplate.execute(
                "ALTER TABLE orders_archive ALTER COLUMN zip_code RENAME TO postal_code");
    }

    @Test
    @DisplayName("should archive an old order preserving postal_code")
    void should_archiveOldOrder_preservingPostalCode() {
        insertOrder("o-old", "ORD-OLD", LocalDateTime.of(2024, 1, 1, 0, 0), "1000 AA");

        int archived = scheduler.archiveOldOrders();

        assertThat(archived).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT order_number, postal_code FROM orders_archive WHERE id = ?", "o-old");
        assertThat(row.get("postal_code")).isEqualTo("1000 AA");
        Integer liveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, "o-old");
        assertThat(liveCount).isZero();
    }

    @Test
    @DisplayName("should leave recent orders in the live table")
    void should_notArchiveRecentOrders() {
        insertOrder("o-new", "ORD-NEW", LocalDateTime.of(2026, 4, 1, 0, 0), "2000 BB");

        int archived = scheduler.archiveOldOrders();

        assertThat(archived).isZero();
        Integer archiveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders_archive", Integer.class);
        assertThat(archiveCount).isZero();
        Integer liveCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, "o-new");
        assertThat(liveCount).isOne();
    }

    private void insertOrder(String id, String orderNumber, LocalDateTime createdAt,
                             String postalCode) {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, order_number, user_id, subtotal, tax, shipping_cost, total,
                    status, street, city, state, postal_code, country,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, orderNumber, "user-1",
                new java.math.BigDecimal("10.00"), new java.math.BigDecimal("1.00"),
                new java.math.BigDecimal("5.00"), new java.math.BigDecimal("16.00"),
                "DELIVERED", "1 Main St", "Amsterdam", "NH", postalCode, "NL",
                createdAt, createdAt);
    }
}
