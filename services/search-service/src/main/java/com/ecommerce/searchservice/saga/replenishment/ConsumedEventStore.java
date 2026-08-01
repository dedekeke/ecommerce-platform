package com.ecommerce.searchservice.saga.replenishment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed idempotency ledger for the search-service participant.
 *
 * <p>Uses {@code SET key value NX EX <ttl>} so the claim is atomic and
 * survives JVM restarts. Keys live for {@code ttl} (default 24h), long
 * enough to cover any plausible Kafka redelivery window without growing
 * Redis memory unboundedly.
 */
@Component
@Slf4j
public class ConsumedEventStore {

    static final String KEY_PREFIX = "search-service:saga:replenishment:consumed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public ConsumedEventStore(
            StringRedisTemplate redisTemplate,
            @Value("${search.replenishment.consumed-event-ttl-hours:24}") long ttlHours
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Atomically claim ownership of an event. Returns {@code true} on first
     * sight, {@code false} on replay. Null event ids are treated as
     * always-claimable so undecorated test fixtures keep working.
     */
    public boolean tryClaim(UUID eventId) {
        if (eventId == null) {
            return true;
        }
        String key = KEY_PREFIX + eventId;
        try {
            Boolean ok = redisTemplate.execute((RedisCallback<Boolean>) connection ->
                    connection.stringCommands().set(
                            key.getBytes(StandardCharsets.UTF_8),
                            "1".getBytes(StandardCharsets.UTF_8),
                            Expiration.from(ttl),
                            RedisStringCommands.SetOption.SET_IF_ABSENT));
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            // If Redis is unreachable we fail-open: better to risk a duplicate
            // notification than to drop every event. The downstream emitter is
            // already best-effort.
            log.error("Redis claim failed for eventId={} — failing open", eventId, e);
            return true;
        }
    }
}
