-- ============================================
-- V5 — Subscription / recurring orders (§3.6)
-- ============================================
-- Recurring purchases for users who opt in to scheduled re-orders. The
-- SubscriptionScheduler polls this table for due rows
-- (status=ACTIVE AND next_run_at < now()) and creates an order via the
-- existing OrderService, then advances next_run_at by interval_days.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entity at
-- com.ecommerce.orderservice.subscription.Subscription.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS subscriptions (
    id                    BIGSERIAL    PRIMARY KEY,
    user_id               VARCHAR(128) NOT NULL,
    product_id            VARCHAR(128) NOT NULL,
    quantity              INT          NOT NULL,
    interval_days         INT          NOT NULL,
    payment_method_id     VARCHAR(128),
    shipping_address_json TEXT         NOT NULL,
    status                VARCHAR(32)  NOT NULL,  -- ACTIVE, PAUSED, CANCELLED
    next_run_at           TIMESTAMP    NOT NULL,
    last_run_at           TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Drives the scheduler's "find due active subscriptions" query — partial
-- index narrows it to ACTIVE rows only, the only ones the poller cares about.
CREATE INDEX IF NOT EXISTS idx_subscriptions_due
    ON subscriptions (next_run_at)
    WHERE status = 'ACTIVE';

-- Drives the per-user list endpoint (GET /api/subscriptions/user/{userId}).
CREATE INDEX IF NOT EXISTS idx_subscriptions_user
    ON subscriptions (user_id);
