package com.ecommerce.orderservice.saga.rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, String> {

    Optional<Return> findByRmaNumber(String rmaNumber);

    List<Return> findByUserId(String userId);

    /**
     * Load a single return with its {@code lines} eagerly fetched in one query.
     * Used by paths that need the lines after the persistence context closes
     * (refund saga / inspection, single-return GET) now that {@code lines} is
     * {@code LAZY} — avoids {@code LazyInitializationException}.
     */
    @Query("SELECT r FROM Return r LEFT JOIN FETCH r.lines WHERE r.id = :id")
    Optional<Return> findByIdWithLines(@Param("id") String id);

    /**
     * List a user's returns with {@code lines} fetched in a single query.
     * Replaces the N+1 that the previous {@code EAGER} mapping caused on the
     * listing path (one extra select per row). {@code DISTINCT} collapses the
     * join cartesian product back to one row per return.
     */
    @Query("SELECT DISTINCT r FROM Return r LEFT JOIN FETCH r.lines WHERE r.userId = :userId")
    List<Return> findByUserIdWithLines(@Param("userId") String userId);

    List<Return> findByOrderIdAndStatusIn(String orderId, List<ReturnStatus> statuses);

    /**
     * Used by the recovery scheduler — picks up RMAs stuck in transient states
     * (REQUESTED / NOTIFIED / INSPECTING). {@link ReturnStatus#AWAITING_SHIPMENT}
     * is intentionally excluded since that state is supposed to last days.
     */
    List<Return> findByStatusInAndUpdatedAtBefore(List<ReturnStatus> statuses, LocalDateTime cutoff);

    /**
     * Recovery-scheduler variant of {@link #findByStatusInAndUpdatedAtBefore}
     * that {@code JOIN FETCH}es {@code lines} in one query. The scheduler's
     * read tx commits before {@code resume()} reaches {@code handleApproved()},
     * detaching the entities; without the fetch, touching {@code rma.getLines()}
     * for an INSPECTING/APPROVED partial return throws
     * {@code LazyInitializationException} (now that {@code lines} is {@code LAZY}).
     * {@code DISTINCT} collapses the join cartesian product to one row per return.
     */
    @Query("SELECT DISTINCT r FROM Return r LEFT JOIN FETCH r.lines "
        + "WHERE r.status IN :statuses AND r.updatedAt < :cutoff")
    List<Return> findByStatusInAndUpdatedAtBeforeWithLines(
        @Param("statuses") List<ReturnStatus> statuses, @Param("cutoff") LocalDateTime cutoff);
}
