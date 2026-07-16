-- ============================================
-- Saved payment methods: idempotency constraint (§3.9)
-- ============================================
-- The secure add-a-card flow can persist the same tokenized method twice: once
-- from the browser's POST /confirm and once from the authoritative
-- setup_intent.succeeded webhook. A unique (user_id, provider_id) pair lets both
-- paths converge on a single row — the second insert is rejected and the caller
-- returns the existing method instead of creating a duplicate.
--
-- Mirrors the SavedPaymentMethod JPA @UniqueConstraint. Postgres dialect.

ALTER TABLE saved_payment_methods
    ADD CONSTRAINT uq_saved_payment_user_provider UNIQUE (user_id, provider_id);
