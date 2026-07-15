package com.ecommerce.orderservice.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, String> {
}
