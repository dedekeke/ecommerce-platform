package com.ecommerce.orderservice.saga.rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, String> {

    Optional<Return> findByRmaNumber(String rmaNumber);

    List<Return> findByUserId(String userId);

    List<Return> findByOrderIdAndStatusIn(String orderId, List<ReturnStatus> statuses);

    /**
     * Used by the recovery scheduler — picks up RMAs stuck in transient states
     * (REQUESTED / NOTIFIED / INSPECTING). {@link ReturnStatus#AWAITING_SHIPMENT}
     * is intentionally excluded since that state is supposed to last days.
     */
    List<Return> findByStatusInAndUpdatedAtBefore(List<ReturnStatus> statuses, LocalDateTime cutoff);
}
