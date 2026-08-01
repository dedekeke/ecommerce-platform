-- ============================================
-- V2 — Performance index audit (2026-04-30)
-- ============================================
-- Reference DDL. The order-service uses Hibernate `ddl-auto: update` for
-- schema management today; the @Index annotations on Order / OrderItem are
-- the source of truth and Hibernate creates these on next startup. This file
-- exists as the canonical contract for downstream tooling (Flyway adoption,
-- ops audits, infra-as-code) — see docs/DB_INDEX_AUDIT.md for rationale.
--
-- Postgres dialect.

-- orders.payment_intent_id — OrderRepository.findByPaymentIntentId is on the
-- payment-callback critical path; without an index this is a full table scan.
CREATE INDEX IF NOT EXISTS idx_order_payment_intent
    ON orders (payment_intent_id);

-- orders (status, created_at) — OrderRepository.findAbandonedOrders filters
-- on (status = PENDING AND created_at < cutoff). The existing
-- idx_user_status_date covers user-scoped queries; this index covers the
-- cron path that scans the whole table by status.
CREATE INDEX IF NOT EXISTS idx_order_status_created
    ON orders (status, created_at);

-- order_items.order_id — JPA does not declare a FK index automatically and
-- PostgreSQL does not auto-index FK columns. The "load items for order N"
-- join is the dominant access path for this table.
CREATE INDEX IF NOT EXISTS idx_order_item_order
    ON order_items (order_id);

-- order_items.product_id — supports reverse lookups (orders containing a
-- given product) used by the refund saga and analytics jobs.
CREATE INDEX IF NOT EXISTS idx_order_item_product
    ON order_items (product_id);
