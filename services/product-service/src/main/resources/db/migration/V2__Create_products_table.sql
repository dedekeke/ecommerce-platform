-- Create products table
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    length DECIMAL(10, 2),
    width DECIMAL(10, 2),
    height DECIMAL(10, 2),
    weight DECIMAL(10, 2),
    stock_quantity INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_product_sku (sku),
    INDEX idx_product_name (name),
    INDEX idx_product_category (category_id),
    INDEX idx_product_active (active),
    INDEX idx_product_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create product_images table for multiple images per product
CREATE TABLE product_images (
    product_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,

    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_product_images_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert some sample products
INSERT INTO products (sku, name, description, category_id, price, currency, stock_quantity, active, length, width, height, weight) VALUES
('LAPTOP-001', 'Dell XPS 13', 'High-performance ultrabook with 13-inch display', 6, 1299.99, 'USD', 15, TRUE, 30.0, 20.0, 1.5, 1.2),
('LAPTOP-002', 'MacBook Pro 14', 'Apple MacBook Pro with M3 chip', 6, 1999.99, 'USD', 10, TRUE, 31.0, 22.0, 1.6, 1.6),
('PHONE-001', 'iPhone 15 Pro', 'Latest iPhone with A17 Pro chip', 7, 999.99, 'USD', 25, TRUE, 14.7, 7.1, 0.8, 0.187),
('PHONE-002', 'Samsung Galaxy S24', 'Flagship Android smartphone', 7, 899.99, 'USD', 20, TRUE, 14.6, 7.0, 0.8, 0.167),
('HEADPHONE-001', 'Sony WH-1000XM5', 'Premium noise-cancelling headphones', 8, 399.99, 'USD', 30, TRUE, 18.0, 18.0, 8.0, 0.250),
('SHIRT-001', 'Classic White Shirt', 'Cotton dress shirt for men', 9, 49.99, 'USD', 100, TRUE, 30.0, 20.0, 2.0, 0.200),
('JEANS-001', 'Blue Denim Jeans', 'Classic fit blue jeans for women', 10, 79.99, 'USD', 75, TRUE, 30.0, 20.0, 3.0, 0.500);

-- Insert sample images for products
INSERT INTO product_images (product_id, image_url) VALUES
(1, 'https://example.com/images/laptop-001-1.jpg'),
(1, 'https://example.com/images/laptop-001-2.jpg'),
(2, 'https://example.com/images/laptop-002-1.jpg'),
(3, 'https://example.com/images/phone-001-1.jpg'),
(3, 'https://example.com/images/phone-001-2.jpg'),
(4, 'https://example.com/images/phone-002-1.jpg'),
(5, 'https://example.com/images/headphone-001-1.jpg'),
(6, 'https://example.com/images/shirt-001-1.jpg'),
(7, 'https://example.com/images/jeans-001-1.jpg');
