-- ============================================
-- Promotion Service — Per-User Promotion Targeting (§3.x)
-- ============================================
-- Maps a promotion code to the users it should be announced to. When a
-- promotion has one or more targets, the PromotionEventPublisher resolves each
-- target's email via user-service and publishes a per-user event. When a
-- promotion has no targets it falls back to the broadcast announcement.
--
-- user_id is the Auth0 `sub` claim (e.g. "auth0|abc123"), mirroring how the
-- rest of the platform identifies users. It is not an FK because user data
-- lives in a separate service/database. VARCHAR(255) because OIDC social subs
-- (e.g. "google-oauth2|<numeric>", "windowslive|<long-guid>") can exceed 100
-- chars; 255 matches the platform-wide user id column width.

CREATE TABLE promotion_user_targets (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    promo_code  VARCHAR(50)  NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_promo_user UNIQUE (promo_code, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_promo_target_code ON promotion_user_targets(promo_code);
