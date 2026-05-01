-- V3 — Cart Abandonment Recovery (§3.10)
--
-- Tracks the last time we sent the user a "you left items in your cart" email
-- so we don't spam them: the AbandonedCartScanner skips any cart whose
-- last_abandonment_reminder_at is within the last 7 days.
ALTER TABLE carts
    ADD COLUMN IF NOT EXISTS last_abandonment_reminder_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_cart_reminder_at
    ON carts(last_abandonment_reminder_at);
