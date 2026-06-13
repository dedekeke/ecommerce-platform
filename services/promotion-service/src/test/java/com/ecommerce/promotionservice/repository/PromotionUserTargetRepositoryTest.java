package com.ecommerce.promotionservice.repository;

import com.ecommerce.promotionservice.event.PromotionUserTarget;
import com.ecommerce.promotionservice.event.PromotionUserTargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PromotionUserTarget Repository Tests")
class PromotionUserTargetRepositoryTest {

    @Autowired
    private PromotionUserTargetRepository repository;

    @Test
    @DisplayName("should_persistLongOidcSocialSub_when_userIdExceeds100Chars")
    void should_persistLongOidcSocialSub_when_userIdExceeds100Chars() {
        // Realistic OIDC social sub far longer than the original VARCHAR(100).
        String longSub = "windowslive|" + "a".repeat(150);
        assertThat(longSub.length()).isGreaterThan(100);

        PromotionUserTarget saved = repository.save(PromotionUserTarget.builder()
                .promoCode("WELCOME10")
                .userId(longSub)
                .build());

        assertThat(repository.findById(saved.getId()))
                .get()
                .extracting(PromotionUserTarget::getUserId)
                .isEqualTo(longSub);
    }

    @Test
    @DisplayName("should_findByPromoCode_when_targetsExist")
    void should_findByPromoCode_when_targetsExist() {
        repository.save(PromotionUserTarget.builder().promoCode("VIP").userId("auth0|alice").build());
        repository.save(PromotionUserTarget.builder().promoCode("VIP").userId("auth0|bob").build());
        repository.save(PromotionUserTarget.builder().promoCode("OTHER").userId("auth0|carol").build());

        assertThat(repository.findByPromoCode("VIP")).hasSize(2);
    }
}
