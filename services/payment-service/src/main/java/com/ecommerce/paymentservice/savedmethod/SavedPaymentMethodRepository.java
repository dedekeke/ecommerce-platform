package com.ecommerce.paymentservice.savedmethod;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {

    List<SavedPaymentMethod> findByUserId(String userId);

    List<SavedPaymentMethod> findByUserIdAndIsDefaultTrue(String userId);
}
