package com.ecommerce.promotionservice.loyalty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link LoyaltyController}. Exercises the thin controller
 * delegation; full HTTP integration is covered by the integration test
 * suite (separately).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoyaltyController")
class LoyaltyControllerTest {

    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private LoyaltyController controller;

    @Test
    @DisplayName("getLoyalty_should_return200WithBody_when_serviceProvidesResponse")
    void getLoyalty_should_return200WithBody_when_serviceProvidesResponse() {
        LoyaltyResponse expected = LoyaltyResponse.builder()
                .tier("GOLD")
                .discountPercent(new BigDecimal("10.00"))
                .currentSpend(new BigDecimal("2500"))
                .nextTier("PLATINUM")
                .nextTierAt(new BigDecimal("2500"))
                .build();
        when(loyaltyService.getLoyaltyForUser("user-1")).thenReturn(expected);

        ResponseEntity<LoyaltyResponse> response = controller.getLoyalty("user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
