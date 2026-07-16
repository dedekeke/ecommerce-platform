-- ============================================
-- V15 — Shipping / fulfillment tracking on orders
-- ============================================
-- Adds the single-shipment tracking columns backing the admin mark-shipped /
-- mark-delivered endpoints (POST /api/orders/{id}/mark-shipped, mark-delivered):
--   * carrier          — shipping carrier name (operator-entered under the
--                        default noop ShippingProvider, or carrier-assigned once
--                        a real SHIPPING_PROVIDER integration is wired).
--   * tracking_number  — carrier tracking number surfaced to the customer.
--   * shipped_at       — timestamp of the CONFIRMED/PROCESSING -> SHIPPED move.
--   * delivered_at     — timestamp of the SHIPPED -> DELIVERED move.
--
-- Bounded scope: one shipment per order. Multi-carrier / split-shipment
-- fulfillment is a later item.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service; this
-- file is the authoritative DDL for downstream tooling (Flyway adoption, ops
-- audits) and mirrors the JPA entity
-- com.ecommerce.orderservice.domain.entity.Order#carrier / #trackingNumber /
-- #shippedAt / #deliveredAt.
--
-- Postgres dialect.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS carrier VARCHAR(100);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(100);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
