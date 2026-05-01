-- ============================================
-- V3 — Add performance indexes (DB index audit, 2026-04-30)
-- ============================================
-- Append-only: do not drop existing indexes. See docs/DB_INDEX_AUDIT.md.
--
-- Audit findings on the products table:
--   1. ProductRepository.advancedSearch filters on (active = true) and a category
--      ID derived from a 3-level category traversal. Today the optimizer can use
--      idx_product_active OR idx_product_category but not both — a composite
--      (active, category_id) lets MySQL serve the active+category sub-clause
--      with a single index scan, which is the most common path on the catalogue
--      pages (active products by category).
--
--   2. ProductRepository.findFeaturedProducts orders by recency on active rows.
--      A composite (active, created_at) avoids a filesort on idx_product_active.
--      We do not have a created_at index on this table at all today.
--
-- Both indexes are CREATE INDEX (not unique). MySQL does not support
-- CREATE INDEX IF NOT EXISTS without 8.0.30+; we rely on Flyway's per-version
-- guarantee (V3 only runs once) for idempotency.

CREATE INDEX idx_product_active_category ON products(active, category_id);
CREATE INDEX idx_product_active_created  ON products(active, created_at);
