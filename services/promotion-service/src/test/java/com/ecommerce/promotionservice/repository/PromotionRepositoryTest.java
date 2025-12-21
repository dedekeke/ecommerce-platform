package com.ecommerce.promotionservice.repository;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Promotion Repository Tests")
class PromotionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PromotionRepository promotionRepository;

    private Promotion activePromotion;
    private Promotion inactivePromotion;
    private Promotion expiredPromotion;
    private Promotion futurePromotion;

    @BeforeEach
    void setUp() {
        activePromotion = Promotion.builder()
                .code("ACTIVE20")
                .name("Active 20% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .currentUses(50)
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .active(true)
                .build();

        inactivePromotion = Promotion.builder()
                .code("INACTIVE10")
                .name("Inactive 10% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .startDate(LocalDateTime.now().minusDays(5))
                .endDate(LocalDateTime.now().plusDays(25))
                .active(false)
                .build();

        expiredPromotion = Promotion.builder()
                .code("EXPIRED50")
                .name("Expired 50% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(50))
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().minusDays(1))
                .active(true)
                .build();

        futurePromotion = Promotion.builder()
                .code("FUTURE30")
                .name("Future 30% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(30))
                .startDate(LocalDateTime.now().plusDays(5))
                .endDate(LocalDateTime.now().plusDays(35))
                .active(true)
                .build();

        entityManager.persist(activePromotion);
        entityManager.persist(inactivePromotion);
        entityManager.persist(expiredPromotion);
        entityManager.persist(futurePromotion);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find promotion by code")
    void shouldFindPromotionByCode() {
        Optional<Promotion> found = promotionRepository.findByCode("ACTIVE20");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Active 20% Off");
    }

    @Test
    @DisplayName("Should return empty when code not found")
    void shouldReturnEmptyWhenCodeNotFound() {
        Optional<Promotion> found = promotionRepository.findByCode("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find all active promotions")
    void shouldFindAllActivePromotions() {
        List<Promotion> activePromotions = promotionRepository.findByActiveTrue();

        assertThat(activePromotions)
                .hasSize(3)
                .extracting(Promotion::getCode)
                .containsExactlyInAnyOrder("ACTIVE20", "EXPIRED50", "FUTURE30");
    }

    @Test
    @DisplayName("Should find promotions by type")
    void shouldFindPromotionsByType() {
        Promotion fixedPromotion = Promotion.builder()
                .code("FIXED100")
                .name("Fixed $100 Off")
                .type(PromotionType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(100))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
        entityManager.persist(fixedPromotion);
        entityManager.flush();

        List<Promotion> percentagePromotions = promotionRepository.findByType(PromotionType.PERCENTAGE);
        List<Promotion> fixedPromotions = promotionRepository.findByType(PromotionType.FIXED_AMOUNT);

        assertThat(percentagePromotions).hasSize(4);
        assertThat(fixedPromotions)
                .hasSize(1)
                .extracting(Promotion::getCode)
                .containsExactly("FIXED100");
    }

    @Test
    @DisplayName("Should find active promotions within date range")
    void shouldFindActivePromotionsWithinDateRange() {
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> validPromotions = promotionRepository.findActivePromotionsByDateRange(now);

        assertThat(validPromotions)
                .hasSize(1)
                .extracting(Promotion::getCode)
                .containsExactly("ACTIVE20");
    }

    @Test
    @DisplayName("Should find valid promotion by code")
    void shouldFindValidPromotionByCode() {
        LocalDateTime now = LocalDateTime.now();
        Optional<Promotion> found = promotionRepository.findValidPromotionByCode("ACTIVE20", now);

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("ACTIVE20");
    }

    @Test
    @DisplayName("Should not find inactive promotion by valid code search")
    void shouldNotFindInactivePromotionByValidCodeSearch() {
        LocalDateTime now = LocalDateTime.now();
        Optional<Promotion> found = promotionRepository.findValidPromotionByCode("INACTIVE10", now);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should not find expired promotion by valid code search")
    void shouldNotFindExpiredPromotionByValidCodeSearch() {
        LocalDateTime now = LocalDateTime.now();
        Optional<Promotion> found = promotionRepository.findValidPromotionByCode("EXPIRED50", now);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should not find future promotion by valid code search")
    void shouldNotFindFuturePromotionByValidCodeSearch() {
        LocalDateTime now = LocalDateTime.now();
        Optional<Promotion> found = promotionRepository.findValidPromotionByCode("FUTURE30", now);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should check if code exists")
    void shouldCheckIfCodeExists() {
        boolean exists = promotionRepository.existsByCode("ACTIVE20");
        boolean notExists = promotionRepository.existsByCode("NONEXISTENT");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should save promotion with categories")
    void shouldSavePromotionWithCategories() {
        Promotion promotion = Promotion.builder()
                .code("CAT_PROMO")
                .name("Category Promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
        promotion.getApplicableCategories().add(1L);
        promotion.getApplicableCategories().add(2L);
        promotion.getApplicableCategories().add(3L);

        Promotion saved = promotionRepository.save(promotion);
        entityManager.flush();
        entityManager.clear();

        Promotion found = promotionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getApplicableCategories())
                .hasSize(3)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Should increment current uses when saved")
    void shouldIncrementCurrentUsesWhenSaved() {
        Promotion promotion = promotionRepository.findByCode("ACTIVE20").orElseThrow();
        int initialUses = promotion.getCurrentUses();

        promotion.incrementUsage();
        promotionRepository.save(promotion);
        entityManager.flush();
        entityManager.clear();

        Promotion updated = promotionRepository.findByCode("ACTIVE20").orElseThrow();
        assertThat(updated.getCurrentUses()).isEqualTo(initialUses + 1);
    }
}
