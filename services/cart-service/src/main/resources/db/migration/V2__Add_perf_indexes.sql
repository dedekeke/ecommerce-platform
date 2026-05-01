-- ============================================
-- V2 — Add performance indexes (DB index audit, 2026-04-30)
-- ============================================
-- Append-only: do not drop existing indexes. See docs/DB_INDEX_AUDIT.md.
--
-- Audit finding on the carts table:
--   CartRepository.findAbandonedCarts filters on (updated_at < threshold AND
--   status = X). Today only individual idx_status and (no idx_updated_at)
--   exist on this table, so the abandoned-cart cron does a sequential scan
--   filtered by status, which becomes expensive once carts grow past a few
--   hundred thousand rows.
--
--   A composite (status, updated_at) lets the optimizer use the index for
--   both clauses, with the equality column (status) leading.

CREATE INDEX IF NOT EXISTS idx_cart_status_updated ON carts(status, updated_at);
