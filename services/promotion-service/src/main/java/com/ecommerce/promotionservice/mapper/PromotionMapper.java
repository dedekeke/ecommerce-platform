package com.ecommerce.promotionservice.mapper;

import com.ecommerce.promotionservice.dto.PromotionRequest;
import com.ecommerce.promotionservice.dto.PromotionResponse;
import com.ecommerce.promotionservice.model.Promotion;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PromotionMapper {

    PromotionResponse toResponse(Promotion promotion);

    List<PromotionResponse> toResponseList(List<Promotion> promotions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentUses", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Promotion toEntity(PromotionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentUses", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(PromotionRequest request, @MappingTarget Promotion promotion);
}
