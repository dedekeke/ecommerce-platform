package com.ecommerce.reviewservice.controller;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates the platform's standard exception types into JSON envelopes with
 * appropriate HTTP statuses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Validation failure: {}", ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Validation failure: {}", ex.getMessage());
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "Bad Request", msg);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return body(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return body(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String error, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("status", status.value());
        envelope.put("error", error);
        envelope.put("message", message);
        return ResponseEntity.status(status).body(envelope);
    }
}
