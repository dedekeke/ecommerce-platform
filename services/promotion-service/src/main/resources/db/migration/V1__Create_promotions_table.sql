-- ============================================
-- Promotion Service - Database Schema (MySQL)
-- ============================================
-- Create promotions table
CREATE TABLE promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    min_purchase_amount DECIMAL(10, 2),
    max_uses INT,
    current_uses INT NOT NULL DEFAULT 0,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_discount_value CHECK (discount_value > 0),
    CONSTRAINT chk_min_purchase_amount CHECK (min_purchase_amount IS NULL OR min_purchase_amount >= 0),
    CONSTRAINT chk_current_uses CHECK (current_uses >= 0),
    CONSTRAINT chk_max_uses CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_dates CHECK (end_date > start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create promotion_categories table for applicable categories
CREATE TABLE promotion_categories (
    promotion_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (promotion_id, category_id),
    CONSTRAINT fk_promotion_categories_promotion
        FOREIGN KEY (promotion_id) REFERENCES promotions(id)
        ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX idx_code ON promotions(code);
CREATE INDEX idx_active_dates ON promotions(active, start_date, end_date);
CREATE INDEX idx_type ON promotions(type);
CREATE INDEX idx_created_at ON promotions(created_at);

-- Insert sample data for testing
INSERT INTO promotions (code, name, description, type, discount_value, min_purchase_amount, max_uses, current_uses, start_date, end_date, active, created_at, updated_at)
VALUES
    ('WELCOME10', 'Welcome 10% Off', 'Get 10% off on your first purchase', 'PERCENTAGE', 10.00, 50.00, NULL, 0, '2024-01-01 00:00:00', '2025-12-31 23:59:59', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('SAVE20', 'Save 20%', 'Get 20% off on purchases over $100', 'PERCENTAGE', 20.00, 100.00, 1000, 0, '2024-01-01 00:00:00', '2025-12-31 23:59:59', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('FIXED50', 'Fixed $50 Off', 'Get $50 off on purchases over $200', 'FIXED_AMOUNT', 50.00, 200.00, 500, 0, '2024-01-01 00:00:00', '2025-12-31 23:59:59', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
