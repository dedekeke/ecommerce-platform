-- ============================================
-- V6 — Returns / RMA saga (§3.8)
-- ============================================
-- Long-running RMA saga state. The saga sits in AWAITING_SHIPMENT for
-- hours/days while the customer ships the parcel back, so per-row
-- progress is persisted aggressively. Mirrors the JPA entity at
-- com.ecommerce.orderservice.saga.rma.Return.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and stays in sync with the entity.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS returns (
    id                VARCHAR(36)  PRIMARY KEY,
    rma_number        VARCHAR(64)  UNIQUE NOT NULL,
    order_id          VARCHAR(128) NOT NULL,
    user_id           VARCHAR(128) NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    return_label_url  VARCHAR(512),
    reason            TEXT,
    requested_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at       TIMESTAMP,
    inspected_at      TIMESTAMP,
    outcome           VARCHAR(32),
    condition         VARCHAR(64),
    notes             TEXT,
    refund_saga_id    VARCHAR(64),
    failure_reason    TEXT,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Drives "find returns by order" (eligibility check + admin look-ups).
CREATE INDEX IF NOT EXISTS idx_returns_order ON returns(order_id);

-- Drives the per-user list endpoint (GET /api/returns/user/{userId}).
CREATE INDEX IF NOT EXISTS idx_returns_user ON returns(user_id);

-- Drives the recovery scheduler's "find stuck transient returns" query.
CREATE INDEX IF NOT EXISTS idx_returns_status ON returns(status);
