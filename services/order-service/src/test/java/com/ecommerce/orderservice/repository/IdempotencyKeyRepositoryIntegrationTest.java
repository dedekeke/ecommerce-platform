package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import com.ecommerce.orderservice.domain.enums.IdempotencyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test against embedded H2 — verifies the JPA mapping and the
 * {@code (user_id, idempotency_key)} unique constraint that backs idempotent
 * checkout (mirrored in {@code V11__Create_idempotency_keys.sql}). This is the
 * concurrency guard: a second reservation for the same (user, key) must fail.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class IdempotencyKeyRepositoryIntegrationTest {

    @Autowired
    private IdempotencyKeyRepository repository;

    @Test
    void should_rejectDuplicate_when_sameUserAndKeyReserved() {
        repository.saveAndFlush(reservation("user-1", "key-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(reservation("user-1", "key-1")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allowSameKeyForDifferentUsers_when_reserving() {
        repository.saveAndFlush(reservation("user-1", "shared-key"));
        repository.saveAndFlush(reservation("user-2", "shared-key"));

        assertThat(repository.findByUserIdAndIdempotencyKey("user-1", "shared-key")).isPresent();
        assertThat(repository.findByUserIdAndIdempotencyKey("user-2", "shared-key")).isPresent();
    }

    @Test
    void should_findByUserAndKey_when_recordExists() {
        repository.saveAndFlush(reservation("user-9", "key-9"));

        Optional<IdempotencyKey> found = repository.findByUserIdAndIdempotencyKey("user-9", "key-9");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }

    private IdempotencyKey reservation(String userId, String key) {
        return IdempotencyKey.builder()
            .userId(userId)
            .idempotencyKey(key)
            .status(IdempotencyStatus.IN_PROGRESS)
            .build();
    }
}
