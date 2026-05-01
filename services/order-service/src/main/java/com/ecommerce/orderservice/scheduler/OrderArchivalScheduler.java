package com.ecommerce.orderservice.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Nightly job that moves orders older than {@code order.archival.cutoff-days}
 * from the live {@code orders} table to the {@code orders_archive} table.
 *
 * <p>Designed to be:
 *
 * <ul>
 *     <li><b>Idempotent</b> — INSERT uses {@code WHERE NOT EXISTS} keyed on
 *         {@code order_number}, so a re-run after a partial failure (or a
 *         successful run that the operator triggers manually) inserts no
 *         duplicates. The job uses the inserted-row count as authority for
 *         issuing the matching DELETE — if INSERT inserted 0 rows, DELETE is
 *         skipped, preserving the invariant that we never DELETE a live row
 *         that doesn't have an archive copy.</li>
 *     <li><b>Batched</b> — work happens in chunks of
 *         {@code order.archival.batch-size} (default 1000) so a single nightly
 *         run cannot lock the table for hours. Each batch runs in its own
 *         transaction (Spring's default {@code @Transactional} on
 *         {@link JdbcTemplate#update} via the underlying TransactionTemplate
 *         contract — but here we let each statement be its own auto-commit
 *         to minimize lock window).</li>
 *     <li><b>Toggleable</b> — set
 *         {@code order.archival.enabled=false} to disable the bean's work
 *         without un-deploying.</li>
 * </ul>
 *
 * <p>This is a JDBC scheduler rather than a JPA one because:
 *
 * <ul>
 *     <li>Hibernate's {@code update} statements would round-trip every row
 *         through the persistence context — wasteful for a bulk archival.</li>
 *     <li>The {@code orders_archive} table is intentionally not mapped to a
 *         JPA entity (archival is SQL-only); JPA would be the wrong tool.</li>
 *     <li>Bulk INSERT … SELECT … WHERE NOT EXISTS is a single round-trip in
 *         JDBC, vs. N+1 in JPA.</li>
 * </ul>
 *
 * @see "docs/PARTITIONING.md — archival strategy and cutoff rationale"
 */
@Component
@Slf4j
public class OrderArchivalScheduler {

    /**
     * Copies eligible orders into the archive (idempotent — only inserts
     * rows whose order_number is not already present). The {@code SELECT}
     * is bounded by {@code created_at < ?} (the cutoff) and {@code LIMIT ?}
     * (the batch size); ORDER BY created_at ASC ensures we drain the oldest
     * rows first.
     */
    private static final String INSERT_BATCH_SQL = """
            INSERT INTO orders_archive (
                id, order_number, user_id, subtotal, tax, shipping_cost, total,
                status, payment_intent_id, promotion_code, discount_amount,
                street, city, state, zip_code, country,
                created_at, updated_at, archived_at
            )
            SELECT
                o.id, o.order_number, o.user_id, o.subtotal, o.tax, o.shipping_cost, o.total,
                o.status, o.payment_intent_id, o.promotion_code, o.discount_amount,
                o.street, o.city, o.state, o.zip_code, o.country,
                o.created_at, o.updated_at, CURRENT_TIMESTAMP
            FROM orders o
            WHERE o.created_at < ?
              AND NOT EXISTS (
                  SELECT 1 FROM orders_archive a WHERE a.order_number = o.order_number
              )
            ORDER BY o.created_at ASC
            LIMIT ?
            """;

    /**
     * Removes the rows we just archived from the live table. Targets the
     * same predicate as the INSERT (created_at &lt; cutoff) but only removes
     * rows that ARE present in the archive — so we never delete a live row
     * without an archive copy, even if the operator manually pre-loaded
     * data into orders_archive.
     */
    private static final String DELETE_BATCH_SQL = """
            DELETE FROM orders
            WHERE id IN (
                SELECT o.id FROM orders o
                WHERE o.created_at < ?
                  AND EXISTS (
                      SELECT 1 FROM orders_archive a WHERE a.order_number = o.order_number
                  )
                LIMIT ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final int cutoffDays;
    private final int batchSize;
    private final boolean enabled;

    private final Counter archivedCounter;
    private final Counter errorCounter;
    private final Timer runDurationTimer;

    public OrderArchivalScheduler(
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${order.archival.cutoff-days:365}") int cutoffDays,
            @Value("${order.archival.batch-size:1000}") int batchSize,
            @Value("${order.archival.enabled:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.cutoffDays = cutoffDays;
        this.batchSize = batchSize;
        this.enabled = enabled;

        this.archivedCounter = Counter.builder("order.archival.rows_archived")
                .description("Total rows moved from orders to orders_archive")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("order.archival.errors")
                .description("Number of archival batches that failed")
                .register(meterRegistry);
        this.runDurationTimer = Timer.builder("order.archival.run_duration")
                .description("Wall-clock duration of a full archival run")
                .register(meterRegistry);
    }

    /**
     * Daily run at {@code order.archival.cron} (default 04:00 UTC).
     * The cron is intentionally offset from {@code OrderScheduledTasks}
     * jobs (01:00 sales report, 03:00 cleanup, 04:00 abandoned) so the
     * archival runs after the abandoned-order cancel has had a chance to
     * land — abandoned orders that just got cancelled aren't archive
     * candidates yet (created_at recent) but the temporal ordering keeps
     * the analytics windows clean.
     */
    @Scheduled(cron = "${order.archival.cron:0 0 4 * * ?}")
    public void scheduledRun() {
        log.info("Starting scheduled task: Archive old orders (cutoff = {} days, batch = {})",
                cutoffDays, batchSize);
        try {
            int total = archiveOldOrders();
            log.info("Completed scheduled task: archived {} orders", total);
        } catch (DataAccessException ex) {
            errorCounter.increment();
            log.error("Order archival job failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Public for testability. Loops over batches until a batch archives
     * fewer than {@link #batchSize} rows (signalling no more eligible rows).
     *
     * @return total rows archived across all batches
     */
    public int archiveOldOrders() {
        if (!enabled) {
            log.debug("Order archival disabled — skipping run");
            return 0;
        }

        return runDurationTimer.record(this::doArchive);
    }

    private int doArchive() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(cutoffDays);
        log.debug("Computed archival cutoff: {}", cutoff);

        int totalArchived = 0;
        while (true) {
            int archivedThisBatch = jdbcTemplate.update(INSERT_BATCH_SQL, cutoff, batchSize);
            if (archivedThisBatch == 0) {
                log.debug("No more rows to archive (cutoff = {})", cutoff);
                break;
            }

            int deletedThisBatch = jdbcTemplate.update(DELETE_BATCH_SQL, cutoff, batchSize);
            archivedCounter.increment(archivedThisBatch);
            totalArchived += archivedThisBatch;

            if (deletedThisBatch != archivedThisBatch) {
                // Diagnostic only — DELETE may legitimately remove fewer if
                // a competing transaction archived a row first. We don't
                // fail the job; we just surface the inconsistency.
                log.warn("Archival batch size mismatch: inserted={}, deleted={}",
                        archivedThisBatch, deletedThisBatch);
            }
            log.debug("Archived batch: inserted={}, deleted={}",
                    archivedThisBatch, deletedThisBatch);

            if (archivedThisBatch < batchSize) {
                // Drained — no more eligible rows.
                break;
            }
        }
        return totalArchived;
    }
}
