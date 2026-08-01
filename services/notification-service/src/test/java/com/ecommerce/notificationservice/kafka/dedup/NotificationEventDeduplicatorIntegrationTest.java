package com.ecommerce.notificationservice.kafka.dedup;

import com.ecommerce.notificationservice.BaseMongoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-level idempotency tests against a real MongoDB (Testcontainers). These
 * prove the guarantee at the storage layer — the unique {@code _id} index and
 * the insert-first claim — rather than mock sequencing, which is what makes
 * this the actual fix for the check-then-insert race (cf. PR#119's
 * OrderCompletedProcessorTest against a real transaction).
 *
 * <p>Requires Docker (Testcontainers {@code mongo:7-jammy}).
 */
@Import(NotificationEventDeduplicator.class)
@DisplayName("NotificationEventDeduplicator — MongoDB unique-index dedup")
class NotificationEventDeduplicatorIntegrationTest extends BaseMongoTest {

    @Autowired
    private NotificationEventDeduplicator deduplicator;

    @Autowired
    private ProcessedNotificationEventRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("should_claimOnce_when_sameKeyDeliveredTwice")
    void should_claimOnce_when_sameKeyDeliveredTwice() {
        boolean first = deduplicator.claim("ORDER_CONFIRMATION:order-1", "order.created");
        boolean second = deduplicator.claim("ORDER_CONFIRMATION:order-1", "order.created");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_allowDistinctKeys_when_differentTemplateOrEntity")
    void should_allowDistinctKeys_when_differentTemplateOrEntity() {
        assertThat(deduplicator.claim("ORDER_CONFIRMATION:order-1", "order.created")).isTrue();
        assertThat(deduplicator.claim("PAYMENT_RECEIPT:order-1", "payment.completed")).isTrue();
        assertThat(deduplicator.claim("ORDER_CONFIRMATION:order-2", "order.created")).isTrue();

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("repositoryInsert_should_throwDuplicateKeyException_when_idReused")
    void repositoryInsert_should_throwDuplicateKeyException_when_idReused() {
        repository.insert(ProcessedNotificationEvent.builder()
                .id("REFUND_COMPLETED:order-9").topic("refund.completed").consumedAt(Instant.now()).build());

        // Direct proof of the DB-level unique constraint on _id.
        assertThatThrownBy(() -> repository.insert(ProcessedNotificationEvent.builder()
                .id("REFUND_COMPLETED:order-9").topic("refund.completed").consumedAt(Instant.now()).build()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("should_grantClaimToExactlyOneThread_when_concurrentDeliveriesRace")
    void should_grantClaimToExactlyOneThread_when_concurrentDeliveriesRace() throws Exception {
        int threads = 16;
        String key = "CART_ABANDONED:cart-42";
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(i -> () -> deduplicator.claim(key, "cart.abandoned"))
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(tasks);

            long winners = 0;
            for (Future<Boolean> r : results) {
                if (r.get()) {
                    winners++;
                }
            }
            // The unique index must let exactly one concurrent delivery through.
            assertThat(winners).isEqualTo(1);
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
