package com.ecommerce.orderservice.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Non-locking read of unpublished events. Retained for tooling/tests; the
     * relay uses {@link #claimUnpublishedForUpdate(Pageable)} instead.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublished(Pageable pageable);

    /**
     * Replica-safe claim: {@code SELECT ... FOR UPDATE SKIP LOCKED}. Two relay
     * instances polling concurrently grab disjoint batches instead of each
     * publishing the same rows. The lock is released when this method's own
     * short transaction commits — the batch is then published outside any DB
     * transaction, and consumer-side {@code outbox-event-id} dedup absorbs the
     * rare duplicate produced if another instance re-claims a row in the window
     * between this commit and {@link #markPublished}.
     *
     * <p>{@code @Transactional} (read-write) is mandatory: PostgreSQL rejects
     * {@code SELECT ... FOR UPDATE} inside the read-only transaction Spring Data
     * opens by default for query methods. The {@code lock.timeout = -2} hint is
     * Hibernate's {@code SKIP_LOCKED}; dialects without skip-locked support
     * (e.g. H2 in tests) degrade to a plain {@code FOR UPDATE}.
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> claimUnpublishedForUpdate(Pageable pageable);

    /**
     * Bulk-mark a batch of events as published in its own transaction. Cheaper
     * than per-row updates because Hibernate flushes a single statement.
     */
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id IN :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /**
     * Record a transient publish failure without touching {@code publishedAt},
     * so the next poll retries the row. Runs in its own transaction because the
     * relay is no longer transactional as a whole.
     */
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.attemptCount = :attemptCount, o.lastError = :lastError WHERE o.id = :id")
    int recordFailure(@Param("id") Long id,
                      @Param("attemptCount") int attemptCount,
                      @Param("lastError") String lastError);

    /**
     * Outbox lag gauge — wired to {@code outbox.unpublished.count} via Micrometer.
     */
    long countByPublishedAtIsNull();
}
