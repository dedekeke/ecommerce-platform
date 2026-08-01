-- V4 — Denormalise shopper email onto the cart (§3.10 follow-up)
--
-- The cart only stored the Auth0 `sub` (user_id), so the AbandonedCartScanner
-- emitted cart.abandoned events with userEmail = null and notification-service
-- silently skipped them. We now resolve the email from user-service on first
-- add-to-cart and cache it here so the scanner can populate the event directly.
ALTER TABLE carts
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255);
