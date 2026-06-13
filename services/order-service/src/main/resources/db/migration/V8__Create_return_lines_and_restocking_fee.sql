-- ============================================
-- V8 — Partial returns + restocking fee (§3.8)
-- ============================================
-- Models line-item-level returns: a Return now has many ReturnLine rows
-- (each referencing an order item + quantity + reason). The refund saga sums
-- the APPROVED lines and deducts the restocking fee recorded at inspection.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entities at
-- com.ecommerce.orderservice.saga.rma.{Return,ReturnLine} and
-- com.ecommerce.orderservice.saga.refund.RefundSagaState.
--
-- Postgres dialect.

-- Restocking fee recorded on the parent return at inspection time.
ALTER TABLE returns
    ADD COLUMN IF NOT EXISTS restocking_fee_percent NUMERIC(5, 2);

-- Line-item-level return rows. orphanRemoval + cascade ALL on the entity,
-- so the FK cascades on delete to keep partial returns consistent.
CREATE TABLE IF NOT EXISTS return_lines (
    id              VARCHAR(36)  PRIMARY KEY,
    return_id       VARCHAR(36)  NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    order_item_id   VARCHAR(128) NOT NULL,
    product_id      VARCHAR(128),
    quantity        INT          NOT NULL,
    unit_price      NUMERIC(10, 2),
    reason          TEXT,
    approved        BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Drives "load lines for a return" (refund saga + admin look-ups).
CREATE INDEX IF NOT EXISTS idx_return_lines_return ON return_lines(return_id);

-- Drives traceability from an order item back to its returned lines.
CREATE INDEX IF NOT EXISTS idx_return_lines_order_item ON return_lines(order_item_id);

-- Refund saga now records a partial-refund base + restocking fee so the
-- recovery scheduler reconstructs the same refund amount after a crash.
ALTER TABLE refund_saga_state
    ADD COLUMN IF NOT EXISTS refund_amount_override NUMERIC(10, 2);

ALTER TABLE refund_saga_state
    ADD COLUMN IF NOT EXISTS restocking_fee_percent NUMERIC(5, 2);
