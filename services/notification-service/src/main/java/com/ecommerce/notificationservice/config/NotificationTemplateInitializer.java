package com.ecommerce.notificationservice.config;

import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Initialize default notification templates in the database
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationTemplateInitializer implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing notification templates...");

        // Order Confirmation Template
        if (!templateRepository.existsByCode("ORDER_CONFIRMATION")) {
            Map<String, Object> defaultVars = new HashMap<>();
            defaultVars.put("companyName", "E-Commerce Platform");

            NotificationTemplate orderConfirmation = NotificationTemplate.builder()
                    .code("ORDER_CONFIRMATION")
                    .name("Order Confirmation")
                    .description("Email sent when an order is confirmed")
                    .type(NotificationType.EMAIL)
                    .subject("Your Order #${orderNumber} has been confirmed")
                    .body("order-confirmation")
                    .defaultVariables(defaultVars)
                    .active(true)
                    .build();

            templateRepository.save(orderConfirmation);
            log.info("Created ORDER_CONFIRMATION template");
        }

        // Payment Receipt Template
        if (!templateRepository.existsByCode("PAYMENT_RECEIPT")) {
            Map<String, Object> defaultVars = new HashMap<>();
            defaultVars.put("companyName", "E-Commerce Platform");

            NotificationTemplate paymentReceipt = NotificationTemplate.builder()
                    .code("PAYMENT_RECEIPT")
                    .name("Payment Receipt")
                    .description("Email sent when payment is received")
                    .type(NotificationType.EMAIL)
                    .subject("Payment Received for Order #${orderNumber}")
                    .body("payment-receipt")
                    .defaultVariables(defaultVars)
                    .active(true)
                    .build();

            templateRepository.save(paymentReceipt);
            log.info("Created PAYMENT_RECEIPT template");
        }

        // Shipping Notification Template
        if (!templateRepository.existsByCode("SHIPPING_NOTIFICATION")) {
            Map<String, Object> defaultVars = new HashMap<>();
            defaultVars.put("companyName", "E-Commerce Platform");
            defaultVars.put("estimatedDelivery", "3-5 business days");

            NotificationTemplate shippingNotification = NotificationTemplate.builder()
                    .code("SHIPPING_NOTIFICATION")
                    .name("Shipping Notification")
                    .description("Email sent when an order is shipped")
                    .type(NotificationType.EMAIL)
                    .subject("Your Order #${orderNumber} has been shipped!")
                    .body("shipping-notification")
                    .defaultVariables(defaultVars)
                    .active(true)
                    .build();

            templateRepository.save(shippingNotification);
            log.info("Created SHIPPING_NOTIFICATION template");
        }

        // Cart Abandonment Recovery Template (§3.10)
        if (!templateRepository.existsByCode("CART_ABANDONED")) {
            Map<String, Object> defaultVars = new HashMap<>();
            defaultVars.put("companyName", "E-Commerce Platform");

            NotificationTemplate cartAbandoned = NotificationTemplate.builder()
                    .code("CART_ABANDONED")
                    .name("Cart Abandonment Reminder")
                    .description("Email sent when a user leaves items in their cart for more than 24h")
                    .type(NotificationType.EMAIL)
                    .subject("You left ${totalItems} item(s) in your cart — come back!")
                    .body("cart-abandoned")
                    .defaultVariables(defaultVars)
                    .active(true)
                    .build();

            templateRepository.save(cartAbandoned);
            log.info("Created CART_ABANDONED template");
        }

        if (!templateRepository.existsByCode("PROMOTION_ANNOUNCEMENT")) {
            Map<String, Object> defaultVars = new HashMap<>();
            defaultVars.put("companyName", "E-Commerce Platform");

            NotificationTemplate promotionAnnouncement = NotificationTemplate.builder()
                    .code("PROMOTION_ANNOUNCEMENT")
                    .name("Promotion Announcement")
                    .description("Email sent when a new promotion is created")
                    .type(NotificationType.EMAIL)
                    .subject("New Promotion: ${name} (${promoCode})")
                    .body("promotion-announcement")
                    .defaultVariables(defaultVars)
                    .active(true)
                    .build();

            templateRepository.save(promotionAnnouncement);
            log.info("Created PROMOTION_ANNOUNCEMENT template");
        }

        log.info("Notification templates initialization completed");
    }
}
