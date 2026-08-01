package com.ecommerce.notificationservice.config;

import com.ecommerce.notificationservice.domain.NotificationTemplate;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Initialize default notification templates in the database.
 *
 * <p>Seeding is insert-first: every template is unconditionally inserted and a
 * {@link DuplicateKeyException} (raised by the unique index on
 * {@code NotificationTemplate.code}) is treated as "already seeded". This is
 * race-safe when several service instances boot concurrently — the previous
 * check-then-insert could double-insert because {@code existsByCode} and the
 * subsequent save were not atomic.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationTemplateInitializer implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing notification templates...");

        Map<String, Object> orderVars = defaultVars();
        insertIfAbsent(NotificationTemplate.builder()
                .code("ORDER_CONFIRMATION")
                .name("Order Confirmation")
                .description("Email sent when an order is confirmed")
                .type(NotificationType.EMAIL)
                .subject("Your Order #${orderNumber} has been confirmed")
                .body("order-confirmation")
                .defaultVariables(orderVars)
                .active(true)
                .build());

        insertIfAbsent(NotificationTemplate.builder()
                .code("PAYMENT_RECEIPT")
                .name("Payment Receipt")
                .description("Email sent when payment is received")
                .type(NotificationType.EMAIL)
                .subject("Payment Received for Order #${orderNumber}")
                .body("payment-receipt")
                .defaultVariables(defaultVars())
                .active(true)
                .build());

        Map<String, Object> shippingVars = defaultVars();
        shippingVars.put("estimatedDelivery", "3-5 business days");
        insertIfAbsent(NotificationTemplate.builder()
                .code("SHIPPING_NOTIFICATION")
                .name("Shipping Notification")
                .description("Email sent when an order is shipped")
                .type(NotificationType.EMAIL)
                .subject("Your Order #${orderNumber} has been shipped!")
                .body("shipping-notification")
                .defaultVariables(shippingVars)
                .active(true)
                .build());

        // Cart Abandonment Recovery Template (§3.10)
        insertIfAbsent(NotificationTemplate.builder()
                .code("CART_ABANDONED")
                .name("Cart Abandonment Reminder")
                .description("Email sent when a user leaves items in their cart for more than 24h")
                .type(NotificationType.EMAIL)
                .subject("You left ${totalItems} item(s) in your cart — come back!")
                .body("cart-abandoned")
                .defaultVariables(defaultVars())
                .active(true)
                .build());

        insertIfAbsent(NotificationTemplate.builder()
                .code("PROMOTION_ANNOUNCEMENT")
                .name("Promotion Announcement")
                .description("Email sent when a new promotion is created")
                .type(NotificationType.EMAIL)
                .subject("New Promotion: ${name} (${promoCode})")
                .body("promotion-announcement")
                .defaultVariables(defaultVars())
                .active(true)
                .build());

        // Refund + RMA templates. These back the customer-facing refund/RMA
        // consumers, which route through NotificationService.sendNotification so
        // a transient email failure is retried by NotificationRetryScheduler
        // (the scheduler re-sends by templateCode, so the row MUST exist here).
        // body = the Thymeleaf template file name under resources/templates.
        seedEmailTemplate("REFUND_COMPLETED", "Refund Completed",
                "Email sent when a customer refund is processed",
                "Your refund has been processed", "refund-completed");
        seedEmailTemplate("RMA_REQUESTED", "RMA Requested",
                "Email sent when a return is authorized",
                "Your return has been authorized", "rma-requested");
        seedEmailTemplate("RMA_COMPLETED", "RMA Completed",
                "Email sent when a return is completed",
                "Your return has been completed", "rma-completed");
        seedEmailTemplate("RMA_REJECTED", "RMA Rejected",
                "Email sent when a return is rejected",
                "Your return has been rejected", "rma-rejected");

        // SMS + push variants for the order / shipping events. These are dispatched in addition to
        // the email template when the OrderEvent carries a phone number / device token. Bodies are
        // plain text with ${...} placeholders resolved by NotificationService#processTemplate.
        seedSmsTemplate("ORDER_CONFIRMATION_SMS", "Order Confirmation SMS",
                "SMS sent when an order is confirmed",
                "Hi ${userName}, your order #${orderNumber} is confirmed. Total: ${totalAmount}.");
        seedSmsTemplate("SHIPPING_NOTIFICATION_SMS", "Shipping Notification SMS",
                "SMS sent when an order is shipped",
                "Good news ${userName}! Your order #${orderNumber} has shipped via ${carrier}. "
                        + "Track: ${trackingNumber}.");
        seedPushTemplate("ORDER_CONFIRMATION_PUSH", "Order Confirmation Push",
                "Push sent when an order is confirmed",
                "Order confirmed", "Your order #${orderNumber} is confirmed.");
        seedPushTemplate("SHIPPING_NOTIFICATION_PUSH", "Shipping Notification Push",
                "Push sent when an order is shipped",
                "Order shipped", "Your order #${orderNumber} is on its way! ${carrier} tracking: ${trackingNumber}.");

        log.info("Notification templates initialization completed");
    }

    private void seedEmailTemplate(String code, String name, String description,
                                   String subject, String body) {
        insertIfAbsent(NotificationTemplate.builder()
                .code(code)
                .name(name)
                .description(description)
                .type(NotificationType.EMAIL)
                .subject(subject)
                .body(body)
                .defaultVariables(defaultVars())
                .active(true)
                .build());
    }

    private void seedSmsTemplate(String code, String name, String description, String body) {
        insertIfAbsent(NotificationTemplate.builder()
                .code(code)
                .name(name)
                .description(description)
                .type(NotificationType.SMS)
                .body(body)
                .active(true)
                .build());
    }

    private void seedPushTemplate(String code, String name, String description,
                                  String title, String body) {
        insertIfAbsent(NotificationTemplate.builder()
                .code(code)
                .name(name)
                .description(description)
                .type(NotificationType.PUSH)
                .subject(title)
                .body(body)
                .active(true)
                .build());
    }

    /**
     * Insert the template, tolerating the case where a concurrent instance (or a
     * previous boot) already seeded it. The unique index on {@code code} makes
     * this atomic — no check-then-insert window.
     */
    private void insertIfAbsent(NotificationTemplate template) {
        try {
            templateRepository.insert(template);
            log.info("Created {} template", template.getCode());
        } catch (DuplicateKeyException alreadySeeded) {
            log.debug("Template {} already present, skipping seed", template.getCode());
        }
    }

    private Map<String, Object> defaultVars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("companyName", "E-Commerce Platform");
        return vars;
    }
}
