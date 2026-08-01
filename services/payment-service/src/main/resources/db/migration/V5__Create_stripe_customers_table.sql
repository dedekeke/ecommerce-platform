-- ============================================
-- Stripe customers (PR#146 defense-in-depth #2)
-- ============================================
-- Maps an internal user to their Stripe Customer id. Attaching a Customer to
-- both the add-card SetupIntent and the checkout PaymentIntent lets Stripe
-- itself bind a saved payment_method to its owning customer — a second layer
-- behind the server-side ownership check. One Stripe customer per user, so
-- user_id is UNIQUE and concurrent creates converge on a single row.
--
-- Hibernate `ddl-auto: update` is the active schema tool; this file is the
-- authoritative DDL for downstream Flyway adoption and ops audits, mirroring
-- the StripeCustomer JPA entity. Postgres dialect.

CREATE TABLE IF NOT EXISTS stripe_customers (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      VARCHAR(128) NOT NULL,
    customer_id  VARCHAR(255) NOT NULL,    -- Stripe Customer id (cus_...)
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stripe_customer_user UNIQUE (user_id)
);
