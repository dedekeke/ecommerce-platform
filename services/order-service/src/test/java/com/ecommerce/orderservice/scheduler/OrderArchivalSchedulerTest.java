package com.ecommerce.orderservice.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentMatcher;

/**
 * Unit tests for {@link OrderArchivalScheduler}.
 *
 * The scheduler does its work via batched JDBC statements (not JPA) for
 * speed — moving 1000-row chunks via {@code INSERT … SELECT … WHERE NOT
 * EXISTS} and then {@code DELETE} from the live table. We mock
 * {@link JdbcTemplate} and assert:
 *
 * <ul>
 *     <li>The cutoff date is computed correctly from the configured
 *         {@code cutoff-days} property and the injected {@link Clock}.</li>
 *     <li>The archival INSERT runs with that cutoff and the configured
 *         batch size.</li>
 *     <li>When a batch archives fewer rows than the batch size, the loop
 *         terminates (no further iterations).</li>
 *     <li>When a re-run reports zero new rows archived (because they were
 *         already in the archive thanks to the WHERE NOT EXISTS guard), the
 *         DELETE is still skipped — verifying the idempotency contract.</li>
 *     <li>When archival is disabled via flag, no DB calls fire.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderArchivalScheduler — nightly archival to orders_archive")
class OrderArchivalSchedulerTest {

    private static final int CUTOFF_DAYS = 365;
    private static final int BATCH_SIZE = 1000;
    // Fixed clock at 2026-04-29 04:00:00 UTC so the cutoff is deterministic.
    private static final Instant FIXED_NOW = LocalDateTime.of(2026, 4, 29, 4, 0)
            .toInstant(ZoneOffset.UTC);

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OrderArchivalScheduler scheduler;

    /** Null-safe SQL predicate — Mockito will pass null when matching args
     *  for invocations on other overloaded {@code update} methods. */
    private static ArgumentMatcher<String> sqlContaining(String fragment) {
        return sql -> sql != null && sql.contains(fragment);
    }

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        scheduler = new OrderArchivalScheduler(
                jdbcTemplate,
                new SimpleMeterRegistry(),
                fixedClock,
                CUTOFF_DAYS,
                BATCH_SIZE,
                /* enabled */ true);
    }

    @Nested
    @DisplayName("cutoff date computation")
    class CutoffComputation {

        @Test
        void should_useCutoffDays_when_computingThreshold() {
            // Arrange — first INSERT batch returns 0 archived so the loop
            // terminates immediately and we don't have to stub DELETE.
            when(jdbcTemplate.update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(0);

            // Act
            scheduler.archiveOldOrders();

            // Assert — the cutoff passed to the SQL is now-365d.
            ArgumentCaptor<LocalDateTime> cutoffCaptor =
                    ArgumentCaptor.forClass(LocalDateTime.class);
            verify(jdbcTemplate).update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    cutoffCaptor.capture(),
                    eq(BATCH_SIZE));

            LocalDateTime expected = LocalDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC)
                    .minusDays(CUTOFF_DAYS);
            assertThat(cutoffCaptor.getValue()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("batching")
    class Batching {

        @Test
        void should_loopUntilBatchPartiallyFills_when_archivingMultipleBatches() {
            // Arrange — INSERT returns 1000, 1000, 250 (last batch partial,
            // signalling the table is drained). DELETE matches the INSERT
            // count for each batch.
            when(jdbcTemplate.update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(250);
            when(jdbcTemplate.update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(250);

            // Act
            int totalArchived = scheduler.archiveOldOrders();

            // Assert — three iterations: archive+delete pairs.
            assertThat(totalArchived).isEqualTo(BATCH_SIZE + BATCH_SIZE + 250);
            verify(jdbcTemplate, times(3)).update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
            verify(jdbcTemplate, times(3)).update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
        }

        @Test
        void should_stopImmediately_when_noRowsToArchive() {
            // Arrange — empty table.
            when(jdbcTemplate.update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(0);

            // Act
            int totalArchived = scheduler.archiveOldOrders();

            // Assert — 0 archived, exactly one INSERT probe, no DELETE.
            assertThat(totalArchived).isZero();
            verify(jdbcTemplate, times(1)).update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
            verify(jdbcTemplate, never()).update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        void should_skipDelete_when_rerunFindsAlreadyArchivedRows() {
            // Arrange — INSERT … WHERE NOT EXISTS reports 0 inserted (everything
            // is already in archive from the prior run). Scheduler must NOT
            // delete anything in this case — the live rows are presumed gone
            // already, and deleting based on cutoff alone could remove rows
            // that legitimately don't have an archive copy yet.
            when(jdbcTemplate.update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(0);

            // Act
            int totalArchived = scheduler.archiveOldOrders();

            // Assert — no DELETE issued because nothing was archived.
            assertThat(totalArchived).isZero();
            verify(jdbcTemplate, never()).update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
        }

        @Test
        void should_archiveSecondBatch_when_firstBatchAlreadyArchivedRowsRemain() {
            // Arrange — pretend the INSERT … WHERE NOT EXISTS landed 1000
            // archive rows on the first call (fresh data) and 0 on the
            // second (we hit the boundary where remaining live rows already
            // exist in archive from a prior run). DELETE matches first INSERT,
            // is never asked on the second iteration because INSERT == 0.
            when(jdbcTemplate.update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(BATCH_SIZE)
                    .thenReturn(0);
            when(jdbcTemplate.update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE)))
                    .thenReturn(BATCH_SIZE);

            // Act
            int totalArchived = scheduler.archiveOldOrders();

            // Assert
            assertThat(totalArchived).isEqualTo(BATCH_SIZE);
            verify(jdbcTemplate, times(2)).update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
            verify(jdbcTemplate, times(1)).update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("kill switch")
    class KillSwitch {

        @Test
        void should_doNothing_when_archivalIsDisabled() {
            // Arrange — rebuild with enabled=false.
            Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
            scheduler = new OrderArchivalScheduler(
                    jdbcTemplate,
                    new SimpleMeterRegistry(),
                    fixedClock,
                    CUTOFF_DAYS,
                    BATCH_SIZE,
                    /* enabled */ false);

            // Act
            int totalArchived = scheduler.archiveOldOrders();

            // Assert — no DB calls at all.
            assertThat(totalArchived).isZero();
            verify(jdbcTemplate, never()).update(
                    argThat(sqlContaining("INSERT INTO orders_archive")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
            verify(jdbcTemplate, never()).update(
                    argThat(sqlContaining("DELETE FROM orders")),
                    any(LocalDateTime.class), eq(BATCH_SIZE));
        }
    }
}
