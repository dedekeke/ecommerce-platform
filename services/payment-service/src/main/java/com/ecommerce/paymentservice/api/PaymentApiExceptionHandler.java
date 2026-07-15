package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.service.InvalidPaymentStateException;
import com.ecommerce.paymentservice.service.PaymentNotFoundException;
import com.ecommerce.paymentservice.webhook.WebhookVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates checkout PaymentIntent API errors into structured JSON responses. */
@RestControllerAdvice(basePackages = "com.ecommerce.paymentservice.api")
@Slf4j
public class PaymentApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(PaymentNotFoundException ex) {
        log.warn("Payment not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidPaymentStateException ex) {
        log.warn("Invalid payment state: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(UserMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleUserMismatch(UserMismatchException ex) {
        log.warn("Payment request user mismatch: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookVerification(WebhookVerificationException ex) {
        // Signature verification is the webhook's only authentication. A failure is
        // rejected with 400 (Stripe treats non-2xx as "retry later") and never leaks
        // the underlying reason to the caller.
        log.warn("Rejected Stripe webhook: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Webhook signature verification failed", null);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        // Never leak the underlying exception detail to clients; the full stack trace is logged.
        log.error("Unhandled error in payment API", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing the payment.", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (details != null) {
            body.put("errors", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
