-- ============================================
-- Promotion Service — Loyalty / Tiered Pricing (§3.7)
-- ============================================

CREATE TABLE loyalty_tiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    min_spend DECIMAL(10, 2) NOT NULL,
    discount_percent DECIMAL(5, 2) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_loyalty_min_spend CHECK (min_spend >= 0),
    CONSTRAINT chk_loyalty_discount CHECK (discount_percent >= 0 AND discount_percent <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_loyalty_tier_min_spend ON loyalty_tiers(min_spend);

-- Default tier seeds (§3.7).
-- BRONZE applies to anyone who has spent more than $0; the higher tiers
-- promote shoppers to better discounts as their lifetime spend grows.
INSERT INTO loyalty_tiers (name, min_spend, discount_percent, sort_order)
VALUES
    ('BRONZE',   0.00,    0.00, 1),
    ('SILVER',   500.00,  5.00, 2),
    ('GOLD',     2000.00, 10.00, 3),
    ('PLATINUM', 5000.00, 15.00, 4);

CREATE TABLE customer_spend (
    user_id VARCHAR(100) NOT NULL PRIMARY KEY,
    total_spend DECIMAL(12, 2) NOT NULL DEFAULT 0,
    last_order_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_customer_spend_total CHECK (total_spend >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
