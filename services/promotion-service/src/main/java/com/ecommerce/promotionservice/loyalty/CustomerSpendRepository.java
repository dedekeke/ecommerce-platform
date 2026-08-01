package com.ecommerce.promotionservice.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerSpendRepository extends JpaRepository<CustomerSpend, String> {
}
