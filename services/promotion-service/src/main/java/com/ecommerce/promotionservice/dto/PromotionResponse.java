package com.ecommerce.promotionservice.dto;

import com.ecommerce.promotionservice.model.PromotionType;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponse implements Serializable {

    private Long id;
    private String code;
    private String name;
    private String description;
    private PromotionType type;
    private BigDecimal discountValue;
    private BigDecimal minPurchaseAmount;
    private Integer maxUses;
    private Integer currentUses;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean active;
    private Set<Long> applicableCategories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
