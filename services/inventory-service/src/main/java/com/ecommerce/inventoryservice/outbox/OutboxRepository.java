package com.ecommerce.inventoryservice.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claim the next batch of unpublished events ordered by creation time.
     *
     * <p>The relay calls this inside a transaction. {@code Pageable} caps the
     * batch size so a single poll never tries to flush an unbounded backlog.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublished(Pageable pageable);

    /**
     * Bulk-mark a batch of events as published. Cheaper than per-row updates
     * because Hibernate flushes a single statement.
     */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id IN :ids")
    int markPublished(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    /**
     * Outbox lag gauge — wired to {@code outbox.unpublished.count} via Micrometer.
     */
    long countByPublishedAtIsNull();
}
