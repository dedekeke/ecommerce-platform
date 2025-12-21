package com.ecommerce.promotionservice.exception;

public class PromotionNotFoundException extends RuntimeException {
    public PromotionNotFoundException(Long id) {
        super("Promotion not found with id: " + id);
    }

    public PromotionNotFoundException(String code) {
        super("Promotion not found with code: " + code);
    }
}
