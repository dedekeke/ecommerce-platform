package com.ecommerce.paymentservice.outbox;

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
     * Replica-safe claim: {@code SELECT ... FOR UPDATE SKIP LOCKED}. Concurrent
     * relay instances grab disjoint batches instead of double-publishing. See
     * order-service's copy for the full design notes (claim/publish/mark split,
     * consumer-side dedup safety net, and the read-write transaction requirement
     * for {@code FOR UPDATE} on PostgreSQL).
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> claimUnpublishedForUpdate(Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id IN :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.attemptCount = :attemptCount, o.lastError = :lastError WHERE o.id = :id")
    int recordFailure(@Param("id") Long id,
                      @Param("attemptCount") int attemptCount,
                      @Param("lastError") String lastError);

    long countByPublishedAtIsNull();
}
