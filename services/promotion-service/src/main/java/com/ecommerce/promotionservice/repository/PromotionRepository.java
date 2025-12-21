package com.ecommerce.promotionservice.repository;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCode(String code);

    List<Promotion> findByActiveTrue();

    List<Promotion> findByType(PromotionType type);

    boolean existsByCode(String code);

    @Query("SELECT p FROM Promotion p WHERE p.active = true " +
           "AND p.startDate <= :currentDate " +
           "AND p.endDate > :currentDate")
    List<Promotion> findActivePromotionsByDateRange(@Param("currentDate") LocalDateTime currentDate);

    @Query("SELECT p FROM Promotion p WHERE p.code = :code " +
           "AND p.active = true " +
           "AND p.startDate <= :currentDate " +
           "AND p.endDate > :currentDate " +
           "AND (p.maxUses IS NULL OR p.currentUses < p.maxUses)")
    Optional<Promotion> findValidPromotionByCode(@Param("code") String code,
                                                   @Param("currentDate") LocalDateTime currentDate);
}
