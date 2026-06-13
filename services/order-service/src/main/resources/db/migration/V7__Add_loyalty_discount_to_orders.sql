-- ============================================
-- V7 — Loyalty tier discount on orders (§3.7)
-- ============================================
-- The checkout pricing path now consumes the loyalty tier discount computed
-- by promotion-service and records it as an absolute amount alongside the
-- existing promotion-code discount. Surfaced so the order pricing breakdown
-- (subtotal / discount_amount / loyalty_discount / tax / shipping / total)
-- is fully auditable.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service;
-- this file is the authoritative DDL for downstream tooling (Flyway adoption,
-- ops audits) and mirrors the JPA entity at
-- com.ecommerce.orderservice.domain.entity.Order.
--
-- Postgres dialect.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS loyalty_discount NUMERIC(10, 2);
