package com.ecommerce.orderservice.saga.refund;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundSagaRepository extends JpaRepository<RefundSagaState, String> {

    Optional<RefundSagaState> findByOrderIdAndStatusIn(String orderId, List<RefundSagaStatus> statuses);

    List<RefundSagaState> findByStatusInAndUpdatedAtBefore(List<RefundSagaStatus> statuses, LocalDateTime cutoff);

    Page<RefundSagaState> findByStatus(RefundSagaStatus status, Pageable pageable);
}
