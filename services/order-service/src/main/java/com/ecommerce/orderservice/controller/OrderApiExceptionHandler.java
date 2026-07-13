package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.exception.UserMismatchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates order REST API security errors into structured JSON responses. */
@RestControllerAdvice(basePackages = {
    "com.ecommerce.orderservice.controller",
    "com.ecommerce.orderservice.subscription",
    "com.ecommerce.orderservice.saga"
})
@Slf4j
public class OrderApiExceptionHandler {

    @ExceptionHandler(UserMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleUserMismatch(UserMismatchException ex) {
        log.warn("Order request user mismatch: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
