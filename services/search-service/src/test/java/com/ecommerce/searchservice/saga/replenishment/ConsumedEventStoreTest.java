package com.ecommerce.searchservice.saga.replenishment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumedEventStore — Redis-backed idempotency ledger")
class ConsumedEventStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private ConsumedEventStore store;

    @BeforeEach
    void setUp() {
        store = new ConsumedEventStore(redisTemplate, 24);
    }

    @Test
    @DisplayName("should_returnTrue_when_redisSetNxSucceeds")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_returnTrue_when_redisSetNxSucceeds() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Boolean.TRUE);

        boolean claimed = store.tryClaim(UUID.randomUUID());

        assertThat(claimed).isTrue();
    }

    @Test
    @DisplayName("should_returnFalse_when_keyAlreadyExists")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_returnFalse_when_keyAlreadyExists() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(Boolean.FALSE);

        boolean claimed = store.tryClaim(UUID.randomUUID());

        assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("should_failOpen_when_redisIsDown")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_failOpen_when_redisIsDown() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("connection refused"));

        boolean claimed = store.tryClaim(UUID.randomUUID());

        // Fail-open: prefer a (possibly duplicate) downstream notification
        // over silently dropping every event when Redis is unavailable.
        assertThat(claimed).isTrue();
    }

    @Test
    @DisplayName("should_returnTrue_when_eventIdIsNull")
    void should_returnTrue_when_eventIdIsNull() {
        boolean claimed = store.tryClaim(null);
        assertThat(claimed).isTrue();
    }
}
