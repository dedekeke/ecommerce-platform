-- ============================================
-- V10 — Align orders_archive address column with the live schema (2026-07)
-- ============================================
-- V4 created orders_archive with a `zip_code` column, but the live `orders`
-- table maps the @Embedded Address.postalCode field to `postal_code` (see
-- V1_1 / the Order entity). The OrderArchivalScheduler's INSERT … SELECT copied
-- `o.zip_code` FROM orders and wrote it into orders_archive.zip_code — so the
-- archival job could never run a single batch (SELECT o.zip_code fails against
-- a table that only has postal_code). This migration renames the archive column
-- to postal_code so the archive mirrors `orders` 1:1, as V4 intended.
--
-- Idempotent (belt-and-suspenders, matching the chain's IF-NOT-EXISTS style):
--   - rename only when the old column exists and the new one does not;
--   - otherwise ensure postal_code exists (covers a fresh install whose
--     orders_archive was already created with the corrected shape).
--
-- Postgres dialect.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders_archive' AND column_name = 'zip_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders_archive' AND column_name = 'postal_code'
    ) THEN
        ALTER TABLE orders_archive RENAME COLUMN zip_code TO postal_code;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders_archive' AND column_name = 'postal_code'
    ) THEN
        ALTER TABLE orders_archive ADD COLUMN postal_code VARCHAR(255);
    END IF;
END $$;
