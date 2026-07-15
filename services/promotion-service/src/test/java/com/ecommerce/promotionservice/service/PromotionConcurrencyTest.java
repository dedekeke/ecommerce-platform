package com.ecommerce.promotionservice.service;

import com.ecommerce.promotionservice.dto.DiscountResult;
import com.ecommerce.promotionservice.dto.PromotionRequest;
import com.ecommerce.promotionservice.dto.PromotionValidationRequest;
import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import com.ecommerce.promotionservice.repository.PromotionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency regression test for {@link PromotionService#applyPromotion}.
 *
 * A limited-use promotion is redeemed by many threads at once. Correct behaviour:
 * exactly {@code maxUses} redemptions succeed, the rest are rejected, and the
 * persisted {@code currentUses} never exceeds {@code maxUses}. Without a lock the
 * read-check-increment sequence interleaves and over-redeems (money loss).
 *
 * Determinism is provided by a start {@link CountDownLatch} so every worker races
 * from the same instant; no {@code Thread.sleep} is used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
// This test exercises row-lock/version contention, not pool sizing: threads hold
// a connection while serializing on the hot row, so the pool must exceed the
// worker count or callers starve waiting for a connection.
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=16",
        "spring.datasource.hikari.connection-timeout=20000",
        "grpc.server.port=-1"
})
@DisplayName("Promotion Apply Concurrency Tests")
class PromotionConcurrencyTest {

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private PromotionRepository promotionRepository;

    // Kafka publisher touches no broker in tests, but mock it so no event I/O runs.
    @MockBean
    private com.ecommerce.promotionservice.event.PromotionEventPublisher promotionEventPublisher;

    @AfterEach
    void cleanUp() {
        promotionRepository.deleteAll();
    }

    private Promotion persistPromotion(String code, int maxUses) {
        Promotion promotion = Promotion.builder()
                .code(code)
                .name("Flash Sale")
                .type(PromotionType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10))
                .maxUses(maxUses)
                .currentUses(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .build();
        return promotionRepository.save(promotion);
    }

    private RedemptionOutcome redeemConcurrently(String code, int threads) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger errored = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    PromotionValidationRequest request = PromotionValidationRequest.builder()
                            .code(code)
                            .purchaseAmount(BigDecimal.valueOf(100))
                            .build();
                    DiscountResult result = promotionService.applyPromotion(request);
                    if (result.isValid()) {
                        success.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    errored.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).as("all redemption threads finished").isTrue();
        pool.shutdownNow();
        return new RedemptionOutcome(success.get(), rejected.get(), errored.get(), firstError.get());
    }

    @Test
    @DisplayName("should_redeemOnce_when_maxUsesIsOneAndManyThreadsRace")
    void should_redeemOnce_when_maxUsesIsOneAndManyThreadsRace() throws InterruptedException {
        Promotion promotion = persistPromotion("FLASH1", 1);
        int threads = 10;

        RedemptionOutcome outcome = redeemConcurrently("FLASH1", threads);

        assertThat(outcome.errored()).as("no unexpected exceptions: %s", outcome.firstError()).isZero();
        assertThat(outcome.success()).as("exactly maxUses redemptions succeed").isEqualTo(1);
        assertThat(outcome.rejected()).as("all other attempts rejected").isEqualTo(threads - 1);

        int persistedUses = promotionRepository.findById(promotion.getId()).orElseThrow().getCurrentUses();
        assertThat(persistedUses).as("persisted currentUses never exceeds maxUses").isEqualTo(1);
    }

    @Test
    @DisplayName("should_redeemExactlyMaxUses_when_manyThreadsRaceLimitedCode")
    void should_redeemExactlyMaxUses_when_manyThreadsRaceLimitedCode() throws InterruptedException {
        int maxUses = 3;
        int threads = 12;
        Promotion promotion = persistPromotion("FLASHK", maxUses);

        RedemptionOutcome outcome = redeemConcurrently("FLASHK", threads);

        assertThat(outcome.errored()).as("no unexpected exceptions: %s", outcome.firstError()).isZero();
        assertThat(outcome.success()).as("exactly maxUses redemptions succeed").isEqualTo(maxUses);
        assertThat(outcome.rejected()).as("remaining attempts rejected").isEqualTo(threads - maxUses);

        int persistedUses = promotionRepository.findById(promotion.getId()).orElseThrow().getCurrentUses();
        assertThat(persistedUses).as("persisted currentUses equals maxUses").isEqualTo(maxUses);
    }

    @Test
    @DisplayName("should_notRewindUsageCounter_when_adminUpdatesConcurrentlyWithRedemptions")
    void should_notRewindUsageCounter_when_adminUpdatesConcurrentlyWithRedemptions() throws InterruptedException {
        // Unlimited cap so every redemption succeeds and the expected total is exact.
        Promotion promotion = persistUnlimitedPromotion("MIXED");
        int redeemThreads = 8;
        int redeemsPerThread = 25;
        int adminUpdates = 40;
        int expectedRedemptions = redeemThreads * redeemsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(redeemThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(redeemThreads + 1);
        AtomicInteger redeemed = new AtomicInteger();
        AtomicInteger errored = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        // Admin thread: repeatedly edits admin-editable fields (name) while
        // redemptions run. A stale save must NOT roll back the usage counter.
        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < adminUpdates; i++) {
                    PromotionRequest edit = PromotionRequest.builder()
                            .code("MIXED")
                            .name("Admin-" + i)
                            .type(PromotionType.PERCENTAGE)
                            .discountValue(BigDecimal.valueOf(15))
                            .startDate(LocalDateTime.now().minusDays(1))
                            .endDate(LocalDateTime.now().plusDays(1))
                            .active(true)
                            .build();
                    promotionService.updatePromotion(promotion.getId(), edit);
                }
            } catch (Exception e) {
                firstError.compareAndSet(null, e);
                errored.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        for (int t = 0; t < redeemThreads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < redeemsPerThread; i++) {
                        DiscountResult result = promotionService.applyPromotion(
                                PromotionValidationRequest.builder()
                                        .code("MIXED")
                                        .purchaseAmount(BigDecimal.valueOf(100))
                                        .build());
                        if (result.isValid()) {
                            redeemed.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    errored.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).as("all workers finished").isTrue();
        pool.shutdownNow();

        assertThat(errored.get()).as("no unexpected exceptions: %s", firstError.get()).isZero();
        assertThat(redeemed.get()).as("every redemption succeeded (unlimited cap)").isEqualTo(expectedRedemptions);

        Promotion persisted = promotionRepository.findById(promotion.getId()).orElseThrow();
        assertThat(persisted.getCurrentUses())
                .as("admin update must not rewind the usage counter — all redemptions counted")
                .isEqualTo(expectedRedemptions);
        assertThat(persisted.getName())
                .as("admin edits still take effect")
                .startsWith("Admin-");
    }

    private Promotion persistUnlimitedPromotion(String code) {
        Promotion promotion = Promotion.builder()
                .code(code)
                .name("Original")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .maxUses(null)
                .currentUses(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .build();
        return promotionRepository.save(promotion);
    }

    private record RedemptionOutcome(int success, int rejected, int errored, Throwable firstError) {
    }
}
