-- ============================================
-- Promotion Service - Optimistic locking guard
-- ============================================
-- Adds the Hibernate @Version column guarding concurrent admin edits of a
-- promotion (create/update). The usage-counter race is handled separately by the
-- atomic conditional update in PromotionRepository.redeemByCode.
--
-- Ordering note: V6 (loyalty-consumer dedup, PR #119) is already on develop; this
-- migration takes the next free version V7.
ALTER TABLE promotions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
