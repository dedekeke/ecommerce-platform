-- ============================================
-- Promotion Service - Optimistic locking guard
-- ============================================
-- Adds the Hibernate @Version column used to serialize concurrent redemptions
-- of a limited-use promotion so currentUses can never exceed maxUses.
--
-- Ordering note: V6 is reserved by the loyalty-consumer dedup change (PR #119,
-- not yet merged). This migration deliberately takes the next free version V7.
-- Whichever of the two lands second must ensure Flyway out-of-order handling is
-- acceptable, or renumber before merge.
ALTER TABLE promotions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
