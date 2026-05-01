-- Wishlists Table
-- Stores per-user product wishlists. Promoted from a localStorage-only frontend stub
-- (frontend/user-dashboard-mfe) to a server-backed bounded context inside user-service
-- to avoid spinning up a new microservice for trivial CRUD.
CREATE TABLE wishlists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id VARCHAR(100) NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_wishlists_user_product UNIQUE (user_id, product_id)
);

-- Indexes
CREATE INDEX idx_wishlists_user_id ON wishlists(user_id);
CREATE INDEX idx_wishlists_product_id ON wishlists(product_id);

COMMENT ON TABLE wishlists IS 'Product wishlist per user. One entry per (user_id, product_id) pair.';
COMMENT ON COLUMN wishlists.product_id IS 'Product identifier from product-service. Stored as VARCHAR to allow non-numeric IDs.';
COMMENT ON COLUMN wishlists.added_at IS 'Timestamp when the user added the product to their wishlist.';
