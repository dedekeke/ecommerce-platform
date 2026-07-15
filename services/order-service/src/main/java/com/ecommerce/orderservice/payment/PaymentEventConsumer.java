package com.ecommerce.orderservice.payment;

import com.ecommerce.orderservice.client.ResilientGrpcClient;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes payment settlement events and drives the order state machine —
 * closing the checkout loop (PR#122 creates the order + PaymentIntent; PR#127's
 * Stripe webhook and the browser confirm both settle the payment; this consumer
 * flips the order to CONFIRMED / CANCELLED accordingly).
 *
 * <p>Thin adapter: it parses the JSON payload + the {@code outbox-event-id}
 * header and delegates the transactional state change to {@link
 * PaymentEventHandler}. For a failed payment it then releases the inventory
 * reservation through the existing resilient gRPC path (reused, not duplicated).
 *
 * <p>Error strategy: a malformed (poison) payload is logged and acked so it
 * cannot block the partition forever, while transient failures inside the
 * handler are allowed to propagate for Kafka redelivery — an unknown order is
 * handled gracefully by the handler (acked, not infinitely retried).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {

    static final String EVENT_ID_HEADER = "outbox-event-id";
    static final String GROUP_ID = "order-service-group";

    private final PaymentEventHandler handler;
    private final ResilientGrpcClient grpcClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "payment.completed",
        groupId = GROUP_ID,
        containerFactory = "paymentEventListenerContainerFactory"
    )
    public void onPaymentCompleted(
        @Payload String message,
        @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader
    ) {
        handlePaymentCompleted(message, decode(eventIdHeader));
    }

    @KafkaListener(
        topics = "payment.failed",
        groupId = GROUP_ID,
        containerFactory = "paymentEventListenerContainerFactory"
    )
    public void onPaymentFailed(
        @Payload String message,
        @Header(name = EVENT_ID_HEADER, required = false) byte[] eventIdHeader
    ) {
        handlePaymentFailed(message, decode(eventIdHeader));
    }

    /** Package-visible core so unit tests can drive it without a broker. */
    void handlePaymentCompleted(String message, String eventId) {
        PaymentEventEnvelope event = parse(message, "payment.completed");
        if (event == null) {
            return;
        }
        handler.onPaymentCompleted(eventId, event);
    }

    /** Package-visible core so unit tests can drive it without a broker. */
    void handlePaymentFailed(String message, String eventId) {
        PaymentEventEnvelope event = parse(message, "payment.failed");
        if (event == null) {
            return;
        }
        if (handler.onPaymentFailed(eventId, event)) {
            releaseReservation(event.orderId());
        }
    }

    private PaymentEventEnvelope parse(String message, String topic) {
        try {
            PaymentEventEnvelope event = objectMapper.readValue(message, PaymentEventEnvelope.class);
            if (event.orderId() == null || event.orderId().isBlank()) {
                log.error("{} event missing orderId — dropping poison message: {}", topic, message);
                return null;
            }
            return event;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse {} event — dropping poison message: {}", topic, message, e);
            return null;
        }
    }

    /**
     * Best-effort reservation release, reusing the resilient inventory client.
     * inventory-service releases by order id, so no persisted reservation id is
     * required; failures fall back to inventory's reservation auto-expiry.
     */
    private void releaseReservation(String orderId) {
        try {
            ReleaseReservationResponse response = grpcClient.releaseReservation(
                ReleaseReservationRequest.newBuilder()
                    .setOrderId(orderId)
                    .setReason("Payment failed - releasing reservation")
                    .build());
            if (!response.getSuccess()) {
                log.warn("Reservation release for order {} did not succeed: {} "
                    + "(inventory auto-expiry is the backstop)", orderId, response.getMessage());
            } else {
                log.info("Released inventory reservation for order {} after payment failure", orderId);
            }
        } catch (RuntimeException e) {
            log.error("Reservation release call failed for order {} — relying on auto-expiry",
                orderId, e);
        }
    }

    private static String decode(byte[] header) {
        return header == null ? null : new String(header, StandardCharsets.UTF_8);
    }
}
