package com.ecommerce.promotionservice.service;

import com.ecommerce.promotionservice.dto.*;
import com.ecommerce.promotionservice.exception.PromotionNotFoundException;
import com.ecommerce.promotionservice.exception.PromotionCodeAlreadyExistsException;
import com.ecommerce.promotionservice.mapper.PromotionMapper;
import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import com.ecommerce.promotionservice.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Promotion Service Tests")
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionMapper promotionMapper;

    @InjectMocks
    private PromotionService promotionService;

    private Promotion validPromotion;
    private PromotionRequest promotionRequest;
    private PromotionResponse promotionResponse;

    @BeforeEach
    void setUp() {
        validPromotion = Promotion.builder()
                .id(1L)
                .code("SAVE20")
                .name("20% Off Sale")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .currentUses(50)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        promotionRequest = PromotionRequest.builder()
                .code("SAVE20")
                .name("20% Off Sale")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        promotionResponse = PromotionResponse.builder()
                .id(1L)
                .code("SAVE20")
                .name("20% Off Sale")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .currentUses(50)
                .startDate(validPromotion.getStartDate())
                .endDate(validPromotion.getEndDate())
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("Create Promotion Tests")
    class CreatePromotionTests {

        @Test
        @DisplayName("Should create promotion successfully")
        void shouldCreatePromotionSuccessfully() {
            when(promotionRepository.existsByCode(promotionRequest.getCode())).thenReturn(false);
            when(promotionMapper.toEntity(promotionRequest)).thenReturn(validPromotion);
            when(promotionRepository.save(any(Promotion.class))).thenReturn(validPromotion);
            when(promotionMapper.toResponse(validPromotion)).thenReturn(promotionResponse);

            PromotionResponse result = promotionService.createPromotion(promotionRequest);

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("SAVE20");
            verify(promotionRepository).save(any(Promotion.class));
        }

        @Test
        @DisplayName("Should throw exception when code already exists")
        void shouldThrowExceptionWhenCodeAlreadyExists() {
            when(promotionRepository.existsByCode(promotionRequest.getCode())).thenReturn(true);

            assertThatThrownBy(() -> promotionService.createPromotion(promotionRequest))
                    .isInstanceOf(PromotionCodeAlreadyExistsException.class)
                    .hasMessageContaining("SAVE20");

            verify(promotionRepository, never()).save(any(Promotion.class));
        }
    }

    @Nested
    @DisplayName("Update Promotion Tests")
    class UpdatePromotionTests {

        @Test
        @DisplayName("Should update promotion successfully")
        void shouldUpdatePromotionSuccessfully() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(validPromotion));
            when(promotionRepository.save(any(Promotion.class))).thenReturn(validPromotion);
            when(promotionMapper.toResponse(validPromotion)).thenReturn(promotionResponse);

            PromotionResponse result = promotionService.updatePromotion(1L, promotionRequest);

            assertThat(result).isNotNull();
            verify(promotionMapper).updateEntity(promotionRequest, validPromotion);
            verify(promotionRepository).save(validPromotion);
        }

        @Test
        @DisplayName("Should throw exception when promotion not found")
        void shouldThrowExceptionWhenPromotionNotFound() {
            when(promotionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.updatePromotion(999L, promotionRequest))
                    .isInstanceOf(PromotionNotFoundException.class);

            verify(promotionRepository, never()).save(any(Promotion.class));
        }
    }

    @Nested
    @DisplayName("Get Promotion Tests")
    class GetPromotionTests {

        @Test
        @DisplayName("Should get promotion by id")
        void shouldGetPromotionById() {
            when(promotionRepository.findById(1L)).thenReturn(Optional.of(validPromotion));
            when(promotionMapper.toResponse(validPromotion)).thenReturn(promotionResponse);

            PromotionResponse result = promotionService.getPromotionById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw exception when promotion not found by id")
        void shouldThrowExceptionWhenPromotionNotFoundById() {
            when(promotionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promotionService.getPromotionById(999L))
                    .isInstanceOf(PromotionNotFoundException.class);
        }

        @Test
        @DisplayName("Should get all active promotions")
        void shouldGetAllActivePromotions() {
            List<Promotion> promotions = List.of(validPromotion);
            when(promotionRepository.findByActiveTrue()).thenReturn(promotions);
            when(promotionMapper.toResponseList(promotions)).thenReturn(List.of(promotionResponse));

            List<PromotionResponse> result = promotionService.getAllActivePromotions();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Delete Promotion Tests")
    class DeletePromotionTests {

        @Test
        @DisplayName("Should delete promotion successfully")
        void shouldDeletePromotionSuccessfully() {
            when(promotionRepository.existsById(1L)).thenReturn(true);

            promotionService.deletePromotion(1L);

            verify(promotionRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent promotion")
        void shouldThrowExceptionWhenDeletingNonExistentPromotion() {
            when(promotionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> promotionService.deletePromotion(999L))
                    .isInstanceOf(PromotionNotFoundException.class);

            verify(promotionRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("Promotion Validation Tests")
    class PromotionValidationTests {

        @Test
        @DisplayName("Should validate promotion successfully")
        void shouldValidatePromotionSuccessfully() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getPromotionCode()).isEqualTo("SAVE20");
        }

        @Test
        @DisplayName("Should fail validation when promotion not found")
        void shouldFailValidationWhenPromotionNotFound() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("INVALID")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("INVALID"), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("not found or not valid");
        }

        @Test
        @DisplayName("Should fail validation when purchase amount below minimum")
        void shouldFailValidationWhenPurchaseAmountBelowMinimum() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(50))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("at least");
        }

        @Test
        @DisplayName("Should fail validation when category not applicable")
        void shouldFailValidationWhenCategoryNotApplicable() {
            validPromotion.getApplicableCategories().add(1L);
            validPromotion.getApplicableCategories().add(2L);

            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .categoryId(3L)
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("not applicable to this category");
        }
    }

    @Nested
    @DisplayName("Discount Calculation Tests")
    class DiscountCalculationTests {

        @Test
        @DisplayName("Should calculate percentage discount correctly")
        void shouldCalculatePercentageDiscountCorrectly() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(40.00));
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(160.00));
        }

        @Test
        @DisplayName("Should calculate fixed amount discount correctly")
        void shouldCalculateFixedAmountDiscountCorrectly() {
            Promotion fixedPromotion = Promotion.builder()
                    .code("FIXED50")
                    .name("$50 Off")
                    .type(PromotionType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(50))
                    .minPurchaseAmount(BigDecimal.valueOf(100))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .active(true)
                    .build();

            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("FIXED50")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("FIXED50"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(fixedPromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        }

        @Test
        @DisplayName("Should not allow discount to exceed purchase amount")
        void shouldNotAllowDiscountToExceedPurchaseAmount() {
            Promotion largePromotion = Promotion.builder()
                    .code("LARGE100")
                    .name("$100 Off")
                    .type(PromotionType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(100))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .active(true)
                    .build();

            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("LARGE100")
                    .purchaseAmount(BigDecimal.valueOf(50))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("LARGE100"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(largePromotion));

            DiscountResult result = promotionService.validatePromotion(request);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
            assertThat(result.getFinalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Apply Promotion Tests")
    class ApplyPromotionTests {

        @Test
        @DisplayName("Should apply promotion and increment usage")
        void shouldApplyPromotionAndIncrementUsage() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(200))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));
            when(promotionRepository.save(any(Promotion.class))).thenReturn(validPromotion);

            DiscountResult result = promotionService.applyPromotion(request);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(40.00));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            verify(promotionRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrentUses()).isEqualTo(51);
        }

        @Test
        @DisplayName("Should not increment usage when validation fails")
        void shouldNotIncrementUsageWhenValidationFails() {
            PromotionValidationRequest request = PromotionValidationRequest.builder()
                    .code("SAVE20")
                    .purchaseAmount(BigDecimal.valueOf(50))
                    .build();

            when(promotionRepository.findValidPromotionByCode(eq("SAVE20"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(validPromotion));

            DiscountResult result = promotionService.applyPromotion(request);

            assertThat(result.isValid()).isFalse();
            verify(promotionRepository, never()).save(any(Promotion.class));
        }
    }
}
