-- ============================================
-- Payment Service — Stripe webhook idempotency ledger
-- ============================================
-- Consumer-side dedup for the Stripe webhook (POST /api/payments/webhook).
-- Stripe delivers webhooks at-least-once and retries any non-2xx response, so
-- the SAME event id can arrive multiple times. This table lets the handler
-- claim an event by its Stripe event id before applying it, so a redelivery
-- (or a concurrent replica racing the same event) never re-applies the
-- settlement — mirroring the loyalty consumer dedup added in PR#119.
--
-- The PRIMARY KEY on event_id is the real guard: insert-first, and a duplicate
-- fails the INSERT before the payment reconciliation runs, rolling back the
-- transaction. Hibernate `ddl-auto: update` is the active runtime schema tool;
-- this file is the authoritative DDL for downstream Flyway adoption and ops
-- audits, matching the V2 precedent. Postgres dialect.

CREATE TABLE IF NOT EXISTS processed_stripe_event (
    event_id    VARCHAR(255) NOT NULL PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
