package com.ecommerce.promotionservice.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionUserTargetRepository extends JpaRepository<PromotionUserTarget, Long> {

    List<PromotionUserTarget> findByPromoCode(String promoCode);
}
