package com.ecommerce.paymentservice.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGatewayResponse {
    private boolean success;
    private String paymentIntentId;
    private String transactionId;
    private String clientSecret;
    private String status;
    private String errorMessage;
}
