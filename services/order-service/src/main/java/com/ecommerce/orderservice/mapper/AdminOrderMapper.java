package com.ecommerce.orderservice.mapper;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.dto.AdminOrderResponse;
import org.springframework.stereotype.Component;

/**
 * Injectable mapper seam from an {@link Order} entity to the admin-facing
 * {@link AdminOrderResponse}. The field-level mapping lives in
 * {@link AdminOrderResponse#from(Order)} (same package as the DTO, so it can
 * reach the PII/secret decisions in one auditable place); this component is the
 * injectable boundary the controller depends on and the unit under test.
 */
@Component
public class AdminOrderMapper {

    public AdminOrderResponse toAdminResponse(Order order) {
        return AdminOrderResponse.from(order);
    }
}
