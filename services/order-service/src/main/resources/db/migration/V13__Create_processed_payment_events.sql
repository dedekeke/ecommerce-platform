-- ============================================
-- V13 — Processed payment-event ledger (consumer-side idempotency)
-- ============================================
-- Backs order-service's consumption of payment.completed / payment.failed.
-- Each Kafka delivery carries the producer's outbox-event-id header; we insert
-- that id here BEFORE applying the state change, inside the SAME transaction as
-- the order mutation. The PRIMARY KEY on event_id is the unique-constraint that
-- makes redelivery a no-op — an at-least-once duplicate is detected and skipped,
-- so a payment event is applied at most once (mirrors PR#119's processed-event
-- pattern; the same dedup shape the outbox_event table uses producer-side).
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entity
-- com.ecommerce.orderservice.payment.ProcessedPaymentEvent.
--
-- Version ordering note: the live migration chain tops out at V12 (this
-- branch's V11/V12). PR#112/#116 (held) reserve V9/V10, and PR#122 added
-- V11/V12, so the next free version is V13. Keep this above their highest if a
-- held branch merges first.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS processed_payment_event (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    order_id     VARCHAR(128),
    processed_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supports "what payment events did this order receive?" during incident review.
CREATE INDEX IF NOT EXISTS idx_processed_payment_event_order
    ON processed_payment_event (order_id);
