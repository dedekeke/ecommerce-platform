package com.ecommerce.paymentservice.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, Long> {

    Optional<StripeCustomer> findByUserId(String userId);
}
