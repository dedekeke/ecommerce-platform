-- ============================================
-- V1.1 — Core order tables (catch-up migration)
-- ============================================
-- CATCH-UP MIGRATION. order-service historically relied on Hibernate
-- `ddl-auto: update` to create its schema, so the CREATE TABLE statements for
-- the core aggregates (orders, order_items) and the refund saga state were
-- never written as Flyway scripts — the migration set jumped from V1 (outbox)
-- to V2 (which adds INDEXES to `orders`/`order_items`) and later V8 (which
-- ALTERs `refund_saga_state`), with a gap where V3 would have been.
--
-- Enabling Flyway + `ddl-auto: validate` (this PR) means a FRESH database must
-- build the full schema from migrations. This file supplies the missing tables
-- and is versioned 1.1 so it runs AFTER V1 (outbox) but BEFORE V2 (which indexes
-- these tables). On an EXISTING ddl-auto-built database, baseline-on-migrate
-- baselines at V1 and every statement here is a no-op via IF NOT EXISTS.
--
-- Columns match the JPA entities' Hibernate mapping (snake_case physical
-- naming). Loyalty/restocking columns are intentionally omitted here — they are
-- added by the later V7/V8 ALTERs so the chain stays faithful to history.
--
-- Postgres dialect.

-- ---- orders (com.ecommerce.orderservice.domain.entity.Order) --------------
CREATE TABLE IF NOT EXISTS orders (
    id                VARCHAR(255)  PRIMARY KEY,
    order_number      VARCHAR(255)  NOT NULL,
    user_id           VARCHAR(255)  NOT NULL,
    subtotal          NUMERIC(10,2) NOT NULL,
    tax               NUMERIC(10,2) NOT NULL,
    shipping_cost     NUMERIC(10,2) NOT NULL,
    total             NUMERIC(10,2) NOT NULL,
    status            VARCHAR(255)  NOT NULL,
    -- @Embedded Address (default field names -> snake_case columns)
    street            VARCHAR(255),
    city              VARCHAR(255),
    state             VARCHAR(255),
    postal_code       VARCHAR(255),
    country           VARCHAR(255),
    payment_intent_id VARCHAR(255),
    promotion_code    VARCHAR(255),
    discount_amount   NUMERIC(10,2),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    CONSTRAINT uq_orders_order_number UNIQUE (order_number)
);

CREATE INDEX IF NOT EXISTS idx_user_id           ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_status            ON orders (status);
CREATE INDEX IF NOT EXISTS idx_created_at        ON orders (created_at);
CREATE INDEX IF NOT EXISTS idx_promotion_code    ON orders (promotion_code);
CREATE INDEX IF NOT EXISTS idx_user_status_date  ON orders (user_id, status, created_at);

-- ---- order_items (com.ecommerce.orderservice.domain.entity.OrderItem) -----
CREATE TABLE IF NOT EXISTS order_items (
    id           VARCHAR(255)  PRIMARY KEY,
    product_id   VARCHAR(255)  NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    price        NUMERIC(10,2) NOT NULL,
    quantity     INTEGER       NOT NULL,
    subtotal     NUMERIC(10,2) NOT NULL,
    order_id     VARCHAR(255)  NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE
);

-- ---- refund_saga_state (…saga.refund.RefundSagaState) ---------------------
-- Base columns only; V8 adds refund_amount_override + restocking_fee_percent.
CREATE TABLE IF NOT EXISTS refund_saga_state (
    id                    VARCHAR(255)  PRIMARY KEY,
    order_id              VARCHAR(255)  NOT NULL,
    user_id               VARCHAR(255),
    reason                VARCHAR(255),
    refund_amount         NUMERIC(10,2),
    payment_intent_id     VARCHAR(255),
    refund_transaction_id VARCHAR(255),
    restoration_id        VARCHAR(255),
    status                VARCHAR(255)  NOT NULL,
    current_step          VARCHAR(255)  NOT NULL,
    failure_reason        VARCHAR(1024),
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refund_saga_order_id   ON refund_saga_state (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_saga_status     ON refund_saga_state (status);
CREATE INDEX IF NOT EXISTS idx_refund_saga_updated_at ON refund_saga_state (updated_at);

-- ---- refund_saga_completed_steps (@ElementCollection on RefundSagaState) --
CREATE TABLE IF NOT EXISTS refund_saga_completed_steps (
    saga_id VARCHAR(255) NOT NULL,
    step    VARCHAR(255),
    CONSTRAINT fk_refund_saga_completed_steps FOREIGN KEY (saga_id)
        REFERENCES refund_saga_state (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refund_saga_completed_steps_saga
    ON refund_saga_completed_steps (saga_id);
