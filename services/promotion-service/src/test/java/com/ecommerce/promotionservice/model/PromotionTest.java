package com.ecommerce.promotionservice.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Promotion Entity Tests")
class PromotionTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Promotion createValidPromotion() {
        return Promotion.builder()
                .code("SAVE20")
                .name("20% Off Sale")
                .description("Get 20% off on all items")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .currentUses(0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("Code Validation Tests")
    class CodeValidationTests {

        @Test
        @DisplayName("Should accept valid code with uppercase letters and numbers")
        void shouldAcceptValidCode() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("SAVE2024");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should accept valid code with hyphens and underscores")
        void shouldAcceptCodeWithHyphensAndUnderscores() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("SAVE_20-OFF");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject blank code")
        void shouldRejectBlankCode() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Promotion code is required"));
        }

        @Test
        @DisplayName("Should reject code that is too short")
        void shouldRejectTooShortCode() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("AB");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("between 3 and 50 characters"));
        }

        @Test
        @DisplayName("Should reject code that is too long")
        void shouldRejectTooLongCode() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("A".repeat(51));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("between 3 and 50 characters"));
        }

        @Test
        @DisplayName("Should reject code with lowercase letters")
        void shouldRejectCodeWithLowercaseLetters() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("save20");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("uppercase letters"));
        }

        @Test
        @DisplayName("Should reject code with special characters")
        void shouldRejectCodeWithSpecialCharacters() {
            Promotion promotion = createValidPromotion();
            promotion.setCode("SAVE@20");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("uppercase letters"));
        }
    }

    @Nested
    @DisplayName("Name Validation Tests")
    class NameValidationTests {

        @Test
        @DisplayName("Should accept valid name")
        void shouldAcceptValidName() {
            Promotion promotion = createValidPromotion();
            promotion.setName("Summer Sale 2024");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject blank name")
        void shouldRejectBlankName() {
            Promotion promotion = createValidPromotion();
            promotion.setName("");

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Promotion name is required"));
        }

        @Test
        @DisplayName("Should reject name that is too long")
        void shouldRejectTooLongName() {
            Promotion promotion = createValidPromotion();
            promotion.setName("A".repeat(101));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("between 3 and 100 characters"));
        }
    }

    @Nested
    @DisplayName("Discount Value Validation Tests")
    class DiscountValueValidationTests {

        @Test
        @DisplayName("Should reject null discount value")
        void shouldRejectNullDiscountValue() {
            Promotion promotion = createValidPromotion();
            promotion.setDiscountValue(null);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Discount value is required"));
        }

        @Test
        @DisplayName("Should reject zero discount value")
        void shouldRejectZeroDiscountValue() {
            Promotion promotion = createValidPromotion();
            promotion.setDiscountValue(BigDecimal.ZERO);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("Should reject negative discount value")
        void shouldRejectNegativeDiscountValue() {
            Promotion promotion = createValidPromotion();
            promotion.setDiscountValue(BigDecimal.valueOf(-10));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("Should validate percentage discount not exceeds 100")
        void shouldValidatePercentageDiscountNotExceeds100() {
            Promotion promotion = createValidPromotion();
            promotion.setType(PromotionType.PERCENTAGE);
            promotion.setDiscountValue(BigDecimal.valueOf(20));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Date Validation Tests")
    class DateValidationTests {

        @Test
        @DisplayName("Should reject null start date")
        void shouldRejectNullStartDate() {
            Promotion promotion = createValidPromotion();
            promotion.setStartDate(null);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("Start date is required"));
        }

        @Test
        @DisplayName("Should reject null end date")
        void shouldRejectNullEndDate() {
            Promotion promotion = createValidPromotion();
            promotion.setEndDate(null);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("End date is required"));
        }

        @Test
        @DisplayName("Should validate dates are valid")
        void shouldValidateDatesAreValid() {
            Promotion promotion = createValidPromotion();
            promotion.setStartDate(LocalDateTime.now());
            promotion.setEndDate(LocalDateTime.now().plusDays(30));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Business Logic Tests")
    class BusinessLogicTests {

        @Test
        @DisplayName("Should return true when promotion is valid and active")
        void shouldReturnTrueWhenPromotionIsValid() {
            Promotion promotion = createValidPromotion();
            promotion.setStartDate(LocalDateTime.now().minusDays(1));
            promotion.setEndDate(LocalDateTime.now().plusDays(30));
            promotion.setActive(true);
            promotion.setMaxUses(100);
            promotion.setCurrentUses(50);

            assertThat(promotion.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should return false when promotion is inactive")
        void shouldReturnFalseWhenPromotionIsInactive() {
            Promotion promotion = createValidPromotion();
            promotion.setActive(false);

            assertThat(promotion.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should return false when promotion has not started")
        void shouldReturnFalseWhenPromotionNotStarted() {
            Promotion promotion = createValidPromotion();
            promotion.setStartDate(LocalDateTime.now().plusDays(1));
            promotion.setEndDate(LocalDateTime.now().plusDays(30));

            assertThat(promotion.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should return false when promotion has ended")
        void shouldReturnFalseWhenPromotionEnded() {
            Promotion promotion = createValidPromotion();
            promotion.setStartDate(LocalDateTime.now().minusDays(30));
            promotion.setEndDate(LocalDateTime.now().minusDays(1));

            assertThat(promotion.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should return false when max uses reached")
        void shouldReturnFalseWhenMaxUsesReached() {
            Promotion promotion = createValidPromotion();
            promotion.setMaxUses(100);
            promotion.setCurrentUses(100);

            assertThat(promotion.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should return true when max uses is null (unlimited)")
        void shouldReturnTrueWhenMaxUsesIsNull() {
            Promotion promotion = createValidPromotion();
            promotion.setMaxUses(null);
            promotion.setCurrentUses(1000);

            assertThat(promotion.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Usage Increment Tests")
    class UsageIncrementTests {

        @Test
        @DisplayName("Should increment usage count")
        void shouldIncrementUsageCount() {
            Promotion promotion = createValidPromotion();
            promotion.setCurrentUses(10);

            promotion.incrementUsage();

            assertThat(promotion.getCurrentUses()).isEqualTo(11);
        }

        @Test
        @DisplayName("Should throw exception when incrementing beyond max uses")
        void shouldThrowExceptionWhenIncrementingBeyondMaxUses() {
            Promotion promotion = createValidPromotion();
            promotion.setMaxUses(10);
            promotion.setCurrentUses(10);

            assertThatThrownBy(() -> promotion.incrementUsage())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maximum usage limit");
        }

        @Test
        @DisplayName("Should allow increment when max uses is null")
        void shouldAllowIncrementWhenMaxUsesIsNull() {
            Promotion promotion = createValidPromotion();
            promotion.setMaxUses(null);
            promotion.setCurrentUses(1000);

            assertThatCode(() -> promotion.incrementUsage()).doesNotThrowAnyException();
            assertThat(promotion.getCurrentUses()).isEqualTo(1001);
        }
    }

    @Nested
    @DisplayName("Category Applicability Tests")
    class CategoryApplicabilityTests {

        @Test
        @DisplayName("Should return true when applicable categories is empty")
        void shouldReturnTrueWhenApplicableCategoriesIsEmpty() {
            Promotion promotion = createValidPromotion();
            promotion.getApplicableCategories().clear();

            assertThat(promotion.isApplicableToCategory(1L)).isTrue();
            assertThat(promotion.isApplicableToCategory(999L)).isTrue();
        }

        @Test
        @DisplayName("Should return true when category is in applicable categories")
        void shouldReturnTrueWhenCategoryIsApplicable() {
            Promotion promotion = createValidPromotion();
            promotion.getApplicableCategories().add(1L);
            promotion.getApplicableCategories().add(2L);

            assertThat(promotion.isApplicableToCategory(1L)).isTrue();
            assertThat(promotion.isApplicableToCategory(2L)).isTrue();
        }

        @Test
        @DisplayName("Should return false when category is not in applicable categories")
        void shouldReturnFalseWhenCategoryIsNotApplicable() {
            Promotion promotion = createValidPromotion();
            promotion.getApplicableCategories().add(1L);
            promotion.getApplicableCategories().add(2L);

            assertThat(promotion.isApplicableToCategory(3L)).isFalse();
        }
    }

    @Nested
    @DisplayName("Minimum Purchase Amount Tests")
    class MinimumPurchaseAmountTests {

        @Test
        @DisplayName("Should accept null minimum purchase amount")
        void shouldAcceptNullMinimumPurchaseAmount() {
            Promotion promotion = createValidPromotion();
            promotion.setMinPurchaseAmount(null);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should accept zero minimum purchase amount")
        void shouldAcceptZeroMinimumPurchaseAmount() {
            Promotion promotion = createValidPromotion();
            promotion.setMinPurchaseAmount(BigDecimal.ZERO);

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject negative minimum purchase amount")
        void shouldRejectNegativeMinimumPurchaseAmount() {
            Promotion promotion = createValidPromotion();
            promotion.setMinPurchaseAmount(BigDecimal.valueOf(-10));

            Set<ConstraintViolation<Promotion>> violations = validator.validate(promotion);

            assertThat(violations)
                    .isNotEmpty()
                    .anyMatch(v -> v.getMessage().contains("non-negative"));
        }
    }
}
