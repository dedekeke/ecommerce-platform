-- Create categories table with hierarchical structure
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    parent_id BIGINT,
    image_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE CASCADE,
    INDEX idx_category_name (name),
    INDEX idx_category_slug (slug),
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert some sample root categories
INSERT INTO categories (name, slug, description, active, display_order) VALUES
('Electronics', 'electronics', 'Electronic devices and accessories', TRUE, 1),
('Clothing', 'clothing', 'Fashion and apparel', TRUE, 2),
('Home & Garden', 'home-garden', 'Home improvement and garden supplies', TRUE, 3),
('Books', 'books', 'Books and reading materials', TRUE, 4),
('Sports & Outdoors', 'sports-outdoors', 'Sports equipment and outdoor gear', TRUE, 5);

-- Insert some child categories
INSERT INTO categories (name, slug, description, parent_id, active, display_order) VALUES
-- Electronics subcategories
('Computers', 'computers', 'Desktop and laptop computers', 1, TRUE, 1),
('Smartphones', 'smartphones', 'Mobile phones and accessories', 1, TRUE, 2),
('Audio', 'audio', 'Headphones, speakers, and audio equipment', 1, TRUE, 3),
-- Clothing subcategories
('Men''s Clothing', 'mens-clothing', 'Clothing for men', 2, TRUE, 1),
('Women''s Clothing', 'womens-clothing', 'Clothing for women', 2, TRUE, 2),
('Kids'' Clothing', 'kids-clothing', 'Clothing for children', 2, TRUE, 3);
