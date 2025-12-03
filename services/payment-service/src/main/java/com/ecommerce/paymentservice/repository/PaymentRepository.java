package com.ecommerce.paymentservice.repository;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByUserId(String userId);

    List<Payment> findByStatus(PaymentStatus status);
}
