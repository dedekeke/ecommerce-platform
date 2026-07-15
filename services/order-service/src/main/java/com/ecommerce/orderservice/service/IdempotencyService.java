package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import com.ecommerce.orderservice.domain.enums.IdempotencyStatus;
import com.ecommerce.orderservice.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists Idempotency-Key reservations for the REST checkout.
 *
 * <p>Each public method is its own transaction boundary so the reservation is
 * visible to a concurrent request the instant it commits, and so a release runs
 * independently of the (separately transactional) order-creation saga. The
 * unique constraint on {@code (user_id, idempotency_key)} is the real guard;
 * {@link #tryReserve} deliberately runs outside an ambient transaction so a
 * constraint violation surfaces cleanly instead of poisoning a caller's tx.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> find(String userId, String idempotencyKey) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    /**
     * Atomically reserve the key by inserting an {@code IN_PROGRESS} row. Not
     * {@code @Transactional}: the Spring Data save is its own transaction, so a
     * losing insert race rolls back in isolation and returns {@code false}
     * rather than marking an outer transaction rollback-only.
     *
     * @return {@code true} if this caller won the reservation, {@code false} if
     *         a row for {@code (userId, key)} already existed.
     */
    public boolean tryReserve(String userId, String idempotencyKey) {
        try {
            repository.saveAndFlush(IdempotencyKey.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .status(IdempotencyStatus.IN_PROGRESS)
                .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Idempotency key already reserved for user {}: {}", userId, idempotencyKey);
            return false;
        }
    }

    @Transactional
    public void complete(String userId, String idempotencyKey, String orderId) {
        repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(key -> {
            key.setStatus(IdempotencyStatus.COMPLETED);
            key.setOrderId(orderId);
            repository.save(key);
        });
    }

    /** Release a reservation whose saga failed so a genuine retry can proceed. */
    @Transactional
    public void release(String userId, String idempotencyKey) {
        repository.deleteByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }
}
