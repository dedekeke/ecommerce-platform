-- ============================================
-- V2 — Add promotion_products link table
-- ============================================
-- Supports targeting promotions at individual products (in addition to
-- categories). The replenishment saga uses this to find which promotions
-- to pause when a product runs low.
CREATE TABLE promotion_products (
    promotion_id BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    PRIMARY KEY (promotion_id, product_id),
    CONSTRAINT fk_promotion_products_promotion
        FOREIGN KEY (promotion_id) REFERENCES promotions(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reverse-direction lookup is the hot path (find all promotions for a
-- given product). The composite PK already covers (promotion_id, product_id);
-- add a secondary index keyed on product_id alone for the saga query.
CREATE INDEX idx_promotion_products_product ON promotion_products(product_id);
