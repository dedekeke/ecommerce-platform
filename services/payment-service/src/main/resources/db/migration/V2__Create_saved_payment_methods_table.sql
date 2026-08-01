-- ============================================
-- Saved payment methods (§3.9)
-- ============================================
-- Persists tokenized payment instruments that users can re-use at checkout.
-- The provider/provider_id pair links the row to the upstream tokenization
-- service (Stripe today via MockStripeAdapter, real Stripe SDK on swap-in).
-- We never store PAN — only last4/brand/exp* for UX display.
--
-- Hibernate `ddl-auto: update` is the active schema tool; this file is the
-- authoritative DDL for downstream Flyway adoption and ops audits, mirroring
-- the SavedPaymentMethod JPA entity.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS saved_payment_methods (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      VARCHAR(128) NOT NULL,
    provider     VARCHAR(32)  NOT NULL,    -- 'STRIPE', 'MOCK'
    provider_id  VARCHAR(255) NOT NULL,    -- tokenized payment_method id
    last4        VARCHAR(4),
    brand        VARCHAR(32),
    exp_month    INT,
    exp_year     INT,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Drives the per-user list endpoint and the default-toggle uniqueness pass.
CREATE INDEX IF NOT EXISTS idx_saved_payment_user
    ON saved_payment_methods (user_id);
