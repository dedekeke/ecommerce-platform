package com.ecommerce.promotionservice.loyalty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoyaltyService}.
 *
 * <p>Tier seeds (§3.7):
 * <ul>
 *   <li>BRONZE  ≥ $0     → 0%</li>
 *   <li>SILVER  ≥ $500   → 5%</li>
 *   <li>GOLD    ≥ $2000  → 10%</li>
 *   <li>PLATINUM ≥ $5000 → 15%</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoyaltyService")
class LoyaltyServiceTest {

    @Mock
    private LoyaltyTierRepository loyaltyTierRepository;

    @Mock
    private CustomerSpendRepository customerSpendRepository;

    @InjectMocks
    private LoyaltyService loyaltyService;

    private List<LoyaltyTier> seededTiers;

    @BeforeEach
    void setUp() {
        seededTiers = List.of(
                tier("BRONZE", "0", "0", 1),
                tier("SILVER", "500", "5", 2),
                tier("GOLD", "2000", "10", 3),
                tier("PLATINUM", "5000", "15", 4));
    }

    @ParameterizedTest(name = "spend={0} -> tier={1}, discount={2}")
    @CsvSource({
            "0,        BRONZE,   0",
            "0.01,     BRONZE,   0",
            "499.99,   BRONZE,   0",
            "500.00,   SILVER,   5",
            "500.01,   SILVER,   5",
            "1999.99,  SILVER,   5",
            "2000.00,  GOLD,    10",
            "4999.99,  GOLD,    10",
            "5000.00,  PLATINUM, 15",
            "999999.99, PLATINUM, 15"
    })
    @DisplayName("computeResponse — boundary tier mapping")
    void computeResponse_boundaryTierMapping(String spendStr, String expectedTier, String expectedDiscount) {
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(seededTiers);

        LoyaltyResponse response = loyaltyService.computeResponse(new BigDecimal(spendStr));

        assertThat(response.getTier()).isEqualTo(expectedTier);
        assertThat(response.getDiscountPercent()).isEqualByComparingTo(new BigDecimal(expectedDiscount));
        assertThat(response.getCurrentSpend()).isEqualByComparingTo(new BigDecimal(spendStr));
    }

    @Test
    @DisplayName("should_returnNextTierName_andDeltaToReachIt_when_belowMaxTier")
    void should_returnNextTierName_andDeltaToReachIt_when_belowMaxTier() {
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(seededTiers);

        LoyaltyResponse silver = loyaltyService.computeResponse(new BigDecimal("750"));

        assertThat(silver.getTier()).isEqualTo("SILVER");
        assertThat(silver.getNextTier()).isEqualTo("GOLD");
        assertThat(silver.getNextTierAt()).isEqualByComparingTo(new BigDecimal("1250"));
    }

    @Test
    @DisplayName("should_returnNullNextTier_when_onHighestTier")
    void should_returnNullNextTier_when_onHighestTier() {
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(seededTiers);

        LoyaltyResponse platinum = loyaltyService.computeResponse(new BigDecimal("100000"));

        assertThat(platinum.getTier()).isEqualTo("PLATINUM");
        assertThat(platinum.getNextTier()).isNull();
        assertThat(platinum.getNextTierAt()).isNull();
    }

    @Test
    @DisplayName("should_treatNullSpend_asZero")
    void should_treatNullSpend_asZero() {
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(seededTiers);

        LoyaltyResponse response = loyaltyService.computeResponse(null);

        assertThat(response.getTier()).isEqualTo("BRONZE");
        assertThat(response.getCurrentSpend()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("should_returnBronzeWithZeroSpend_when_userHasNoSpendRecord")
    void should_returnBronzeWithZeroSpend_when_userHasNoSpendRecord() {
        when(customerSpendRepository.findById("user-1")).thenReturn(Optional.empty());
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(seededTiers);

        LoyaltyResponse response = loyaltyService.getLoyaltyForUser("user-1");

        assertThat(response.getTier()).isEqualTo("BRONZE");
        assertThat(response.getCurrentSpend()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("recordSpend_should_createNewRecord_when_userHasNoExistingSpend")
    void recordSpend_should_createNewRecord_when_userHasNoExistingSpend() {
        when(customerSpendRepository.findById("user-1")).thenReturn(Optional.empty());
        when(customerSpendRepository.save(any(CustomerSpend.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerSpend saved = loyaltyService.recordSpend("user-1", new BigDecimal("75.00"),
                LocalDateTime.parse("2026-04-29T10:00:00"));

        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getTotalSpend()).isEqualByComparingTo("75.00");
        assertThat(saved.getLastOrderAt()).isEqualTo(LocalDateTime.parse("2026-04-29T10:00:00"));
        verify(customerSpendRepository, times(1)).save(any(CustomerSpend.class));
    }

    @Test
    @DisplayName("recordSpend_should_accumulate_when_userAlreadyHasSpend")
    void recordSpend_should_accumulate_when_userAlreadyHasSpend() {
        CustomerSpend existing = CustomerSpend.builder()
                .userId("user-1")
                .totalSpend(new BigDecimal("100.00"))
                .build();
        when(customerSpendRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(customerSpendRepository.save(any(CustomerSpend.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerSpend updated = loyaltyService.recordSpend("user-1", new BigDecimal("450.50"), null);

        assertThat(updated.getTotalSpend()).isEqualByComparingTo("550.50");
    }

    @Test
    @DisplayName("recordSpend_should_skip_when_amountInvalid")
    void recordSpend_should_skip_when_amountInvalid() {
        assertThat(loyaltyService.recordSpend("user-1", null, null)).isNull();
        assertThat(loyaltyService.recordSpend("user-1", BigDecimal.ZERO, null)).isNull();
        assertThat(loyaltyService.recordSpend("user-1", new BigDecimal("-1"), null)).isNull();
        assertThat(loyaltyService.recordSpend(null, BigDecimal.TEN, null)).isNull();
        assertThat(loyaltyService.recordSpend("", BigDecimal.TEN, null)).isNull();
        verify(customerSpendRepository, never()).save(any());
    }

    @Test
    @DisplayName("computeResponse_should_returnSafeDefault_when_noTiersConfigured")
    void computeResponse_should_returnSafeDefault_when_noTiersConfigured() {
        when(loyaltyTierRepository.findAllByOrderByMinSpendAsc()).thenReturn(List.of());

        LoyaltyResponse response = loyaltyService.computeResponse(new BigDecimal("1000"));

        assertThat(response.getTier()).isEqualTo("BRONZE");
        assertThat(response.getDiscountPercent()).isEqualByComparingTo("0");
    }

    private static LoyaltyTier tier(String name, String minSpend, String discount, int order) {
        return LoyaltyTier.builder()
                .name(name)
                .minSpend(new BigDecimal(minSpend))
                .discountPercent(new BigDecimal(discount))
                .sortOrder(order)
                .build();
    }
}
