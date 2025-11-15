-- Create carts table
CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_items INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create cart_items table
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100),
    product_image_url VARCHAR(500),
    price_snapshot DECIMAL(10, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_user_id ON carts(user_id);
CREATE INDEX idx_status ON carts(status);
CREATE INDEX idx_expires_at ON carts(expires_at);
CREATE INDEX idx_cart_id ON cart_items(cart_id);
CREATE INDEX idx_product_id ON cart_items(product_id);

-- Comments
COMMENT ON TABLE carts IS 'Shopping carts for users';
COMMENT ON TABLE cart_items IS 'Items in shopping carts';
COMMENT ON COLUMN carts.user_id IS 'Auth0 user ID (sub claim)';
COMMENT ON COLUMN carts.status IS 'Cart status: ACTIVE, CHECKED_OUT, ABANDONED, MERGED';
COMMENT ON COLUMN cart_items.price_snapshot IS 'Product price at time of adding to cart';
