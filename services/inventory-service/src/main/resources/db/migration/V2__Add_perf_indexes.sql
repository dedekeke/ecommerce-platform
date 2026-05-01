-- ============================================
-- V2 — Performance index audit (2026-04-30)
-- ============================================
-- Reference DDL. The inventory-service uses Hibernate `ddl-auto: update` for
-- schema management today; the @Index annotation on InventoryReservation is
-- the source of truth and Hibernate creates the non-partial composite there.
-- This file exists as the canonical contract for downstream tooling (Flyway
-- adoption, ops audits, infra-as-code) — see docs/DB_INDEX_AUDIT.md.
--
-- Postgres dialect.

-- Partial composite for InventoryReservationRepository.findExpiredReservations
-- (WHERE status = 'RESERVED' AND expires_at < :now). RESERVED rows are a small
-- minority of total rows once COMMITTED / RELEASED / EXPIRED dominate, so a
-- partial index keeps the scan surface tiny and dense even at 10M+ rows.
--
-- This is the most cost-effective shape for the expiry cron, but JPA cannot
-- declare partial indexes — hence the V2 SQL is the canonical source.
CREATE INDEX IF NOT EXISTS idx_reservation_status_expires_partial
    ON inventory_reservations (expires_at)
    WHERE status = 'RESERVED';
