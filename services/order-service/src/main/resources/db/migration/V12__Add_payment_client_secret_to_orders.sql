-- ============================================
-- V12 — Persist Stripe PaymentIntent client secret on orders
-- ============================================
-- The REST checkout returns the PaymentIntent client_secret so the browser can
-- confirm payment with Stripe Elements. Persisting it lets an idempotent replay
-- (Idempotency-Key) re-serve the SAME secret to the owning session — the client
-- may never have seen the original response (axios-retry on a lost 2xx), and
-- payment-service exposes no "fetch secret by intent id" RPC. The value is
-- designed to be handed to the paying customer's browser, so storing it beside
-- payment_intent_id is acceptable.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entity
-- com.ecommerce.orderservice.domain.entity.Order#paymentClientSecret.
--
-- Version ordering note: follows V11 from this branch. PR#112/#116 (unmerged)
-- reserve V9/V10; keep V11/V12 above their highest if they merge first.
--
-- Postgres dialect.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_client_secret VARCHAR(255);
