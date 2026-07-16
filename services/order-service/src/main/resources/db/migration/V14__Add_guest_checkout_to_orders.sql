-- ============================================
-- V14 — Guest checkout support on orders
-- ============================================
-- Adds the guest-checkout columns backing POST /api/orders/guest:
--   * guest_order  — true for orders created via the unauthenticated guest path
--                    (their user_id carries the "guest:" prefix). Lets support /
--                    reporting distinguish guest orders without parsing user_id.
--   * guest_email  — normalized (trim + lowercase) email the guest checked out
--                    under. This is the CLAIM KEY: when a user later registers /
--                    verifies this address, OrderService.claimGuestOrders() finds
--                    their guest orders by this column and relinks user_id to the
--                    real account. Indexed for that lookup.
--
-- Guest identity model: user_id = "guest:" + sha256hex(guest_email). Stable, so
-- the order-creation saga resolves the SAME guest cart the browser built; opaque,
-- so the raw email never lands in user_id.
--
-- Hibernate `ddl-auto: update` is the active schema tool for order-service; this
-- file is the authoritative DDL for downstream tooling and mirrors the JPA entity
-- com.ecommerce.orderservice.domain.entity.Order#guestOrder / #guestEmail.
--
-- Postgres dialect.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS guest_order BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS guest_email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_order_guest_email ON orders (guest_email);
