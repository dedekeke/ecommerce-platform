package com.ecommerce.promotionservice.repository;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Atomically redeems one use of a still-valid promotion. The check
     * ({@code currentUses < maxUses}) and the increment happen in a single SQL
     * statement, so concurrent redemptions of the same limited-use code are
     * serialized by the row lock and the cap can never be exceeded (no JVM
     * read-check-increment race, no retry storm).
     *
     * Note: a plain (non-{@code VERSIONED}) update is used deliberately — the
     * HQL {@code UPDATE VERSIONED} form trips a Hibernate SQM-translation
     * concurrency bug ({@code ConcurrentModificationException} in
     * {@code addVersionedAssignment}) under simultaneous first compilation. The
     * counter correctness here comes from the atomic conditional update itself,
     * not from {@code @Version}.
     *
     * @return 1 if a use was redeemed, 0 if the promotion is invalid or exhausted
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Promotion p SET p.currentUses = p.currentUses + 1 " +
           "WHERE p.code = :code " +
           "AND p.active = true " +
           "AND p.startDate <= :currentDate " +
           "AND p.endDate > :currentDate " +
           "AND (p.maxUses IS NULL OR p.currentUses < p.maxUses)")
    int redeemByCode(@Param("code") String code, @Param("currentDate") LocalDateTime currentDate);

    /**
     * Active promotions explicitly linked to a product via the
     * {@code promotion_products} table. Used by the replenishment saga to
     * find promotions to pause when stock runs low.
     */
    @Query("SELECT p FROM Promotion p JOIN p.applicableProducts pp " +
           "WHERE pp = :productId AND p.active = true")
    List<Promotion> findActiveByProductId(@Param("productId") Long productId);

    /**
     * Inactive promotions linked to a product. Used to resume promotions
     * when stock returns. {@code DISTINCT} guards against duplicate rows from
     * the join.
     */
    @Query("SELECT DISTINCT p FROM Promotion p JOIN p.applicableProducts pp " +
           "WHERE pp = :productId AND p.active = false")
    List<Promotion> findInactiveByProductId(@Param("productId") Long productId);
}
