package com.ecommerce.promotionservice.service;

import com.ecommerce.promotionservice.dto.*;
import com.ecommerce.promotionservice.event.PromotionEventPublisher;
import com.ecommerce.promotionservice.exception.PromotionCodeAlreadyExistsException;
import com.ecommerce.promotionservice.exception.PromotionNotFoundException;
import com.ecommerce.promotionservice.mapper.PromotionMapper;
import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import com.ecommerce.promotionservice.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionMapper promotionMapper;
    private final PromotionEventPublisher promotionEventPublisher;

    public PromotionResponse createPromotion(PromotionRequest request) {
        log.info("Creating promotion with code: {}", request.getCode());

        if (promotionRepository.existsByCode(request.getCode())) {
            throw new PromotionCodeAlreadyExistsException(request.getCode());
        }

        Promotion promotion = promotionMapper.toEntity(request);
        Promotion saved = promotionRepository.save(promotion);

        promotionEventPublisher.publishPromotionCreated(saved);

        log.info("Promotion created successfully with id: {}", saved.getId());
        return promotionMapper.toResponse(saved);
    }

    public PromotionResponse updatePromotion(Long id, PromotionRequest request) {
        log.info("Updating promotion with id: {}", id);

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionNotFoundException(id));

        promotionMapper.updateEntity(request, promotion);
        Promotion updated = promotionRepository.save(promotion);

        log.info("Promotion updated successfully with id: {}", id);
        return promotionMapper.toResponse(updated);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "promotions", key = "#id", sync = true)
    public PromotionResponse getPromotionById(Long id) {
        log.debug("Fetching promotion with id: {}", id);

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionNotFoundException(id));

        return promotionMapper.toResponse(promotion);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "promotions", key = "'code:' + #code", sync = true)
    public PromotionResponse getPromotionByCode(String code) {
        log.debug("Fetching promotion with code: {}", code);

        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new PromotionNotFoundException(code));

        return promotionMapper.toResponse(promotion);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "activePromotions", sync = true)
    public List<PromotionResponse> getAllActivePromotions() {
        log.debug("Fetching all active promotions");

        List<Promotion> promotions = promotionRepository.findByActiveTrue();
        return promotionMapper.toResponseList(promotions);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> getAllPromotions() {
        log.debug("Fetching all promotions");

        List<Promotion> promotions = promotionRepository.findAll();
        return promotionMapper.toResponseList(promotions);
    }

    @CacheEvict(value = {"promotions", "activePromotions"}, allEntries = true)
    public void deletePromotion(Long id) {
        log.info("Deleting promotion with id: {}", id);

        if (!promotionRepository.existsById(id)) {
            throw new PromotionNotFoundException(id);
        }

        promotionRepository.deleteById(id);
        log.info("Promotion deleted successfully with id: {}", id);
    }

    @Transactional(readOnly = true)
    public DiscountResult validatePromotion(PromotionValidationRequest request) {
        log.debug("Validating promotion with code: {}", request.getCode());

        LocalDateTime now = LocalDateTime.now();
        Promotion promotion = promotionRepository.findValidPromotionByCode(request.getCode(), now)
                .orElse(null);

        if (promotion == null) {
            return DiscountResult.builder()
                    .valid(false)
                    .message("Promotion code not found or not valid at this time")
                    .build();
        }

        if (promotion.getMinPurchaseAmount() != null &&
            request.getPurchaseAmount().compareTo(promotion.getMinPurchaseAmount()) < 0) {
            return DiscountResult.builder()
                    .valid(false)
                    .message(String.format("Purchase amount must be at least %s to use this promotion",
                            promotion.getMinPurchaseAmount()))
                    .promotionCode(promotion.getCode())
                    .promotionName(promotion.getName())
                    .build();
        }

        if (request.getCategoryId() != null && !promotion.isApplicableToCategory(request.getCategoryId())) {
            return DiscountResult.builder()
                    .valid(false)
                    .message("This promotion is not applicable to this category")
                    .promotionCode(promotion.getCode())
                    .promotionName(promotion.getName())
                    .build();
        }

        BigDecimal discountAmount = calculateDiscount(promotion, request.getPurchaseAmount());
        BigDecimal finalAmount = request.getPurchaseAmount().subtract(discountAmount);

        return DiscountResult.builder()
                .valid(true)
                .message("Promotion is valid")
                .discountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .finalAmount(finalAmount.setScale(2, RoundingMode.HALF_UP))
                .promotionCode(promotion.getCode())
                .promotionName(promotion.getName())
                .build();
    }

    /**
     * Applies a promotion and increments its usage counter. The increment is a
     * single atomic conditional UPDATE ({@link PromotionRepository#redeemByCode})
     * that both checks {@code currentUses < maxUses} and increments in one SQL
     * statement, so concurrent redemptions of the same limited-use code are
     * serialized by the row lock and the cap can never be exceeded (money loss).
     * A zero row count means the code was exhausted between validation and
     * redemption, so we reject.
     */
    @CacheEvict(value = {"promotions", "activePromotions"}, allEntries = true)
    public DiscountResult applyPromotion(PromotionValidationRequest request) {
        log.info("Applying promotion with code: {}", request.getCode());

        DiscountResult validationResult = validatePromotion(request);

        if (!validationResult.isValid()) {
            log.warn("Promotion validation failed for code: {}", request.getCode());
            return validationResult;
        }

        int redeemed = promotionRepository.redeemByCode(request.getCode(), LocalDateTime.now());

        if (redeemed == 0) {
            log.warn("Promotion usage limit reached or no longer valid for code: {}", request.getCode());
            return DiscountResult.builder()
                    .valid(false)
                    .message("Promotion has reached its maximum usage limit")
                    .promotionCode(request.getCode())
                    .promotionName(validationResult.getPromotionName())
                    .build();
        }

        log.info("Promotion applied successfully. Code: {}", request.getCode());
        return validationResult;
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal purchaseAmount) {
        BigDecimal discount;

        switch (promotion.getType()) {
            case PERCENTAGE:
                discount = purchaseAmount
                        .multiply(promotion.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;

            case FIXED_AMOUNT:
                discount = promotion.getDiscountValue();
                break;

            case BUY_X_GET_Y:
                discount = promotion.getDiscountValue();
                break;

            default:
                throw new IllegalArgumentException("Unsupported promotion type: " + promotion.getType());
        }

        if (discount.compareTo(purchaseAmount) > 0) {
            discount = purchaseAmount;
        }

        return discount;
    }
}
