package com.ecommerce.promotionservice.exception;

public class PromotionCodeAlreadyExistsException extends RuntimeException {
    public PromotionCodeAlreadyExistsException(String code) {
        super("Promotion with code already exists: " + code);
    }
}
