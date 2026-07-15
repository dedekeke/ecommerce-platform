-- ============================================
-- V11 — Idempotency keys for POST /api/orders
-- ============================================
-- Backs the Idempotency-Key header on the REST checkout so a client replay
-- (the checkout-mfe uses axios-retry, which re-sends on 5xx) returns the SAME
-- order instead of creating a duplicate. A (user_id, idempotency_key) row is
-- reserved before the order-creation saga runs and marked COMPLETED with the
-- resulting order_id on success; it is released on saga failure so a genuine
-- retry can proceed.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entity at
-- com.ecommerce.orderservice.domain.entity.IdempotencyKey.
--
-- Version ordering note: PR#112 and PR#116 (unmerged at time of writing) add
-- migrations up to V10, so this file claims V11. If either merges after this,
-- keep this file at the next free version above their highest.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id               VARCHAR(36)  PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    user_id          VARCHAR(255) NOT NULL,
    order_id         VARCHAR(36),
    status           VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Idempotency is scoped per user: the same key from two different users is two
-- distinct requests. This unique constraint is the concurrency guard — the
-- second in-flight request with the same (user, key) loses the insert race.
ALTER TABLE idempotency_keys
    ADD CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key);

-- Drives replay look-up "does this order already have a key?" and audits.
CREATE INDEX IF NOT EXISTS idx_idempotency_order ON idempotency_keys(order_id);
