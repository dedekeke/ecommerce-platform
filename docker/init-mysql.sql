-- ============================================
-- MySQL Database Initialization Script
-- E-Commerce Platform - Read-Heavy Services
-- ============================================
-- This script creates databases for services with read-heavy workloads
-- Services: Product, Promotion
-- ============================================

-- Create Product Service Database
CREATE DATABASE IF NOT EXISTS productdb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create Promotion Service Database
CREATE DATABASE IF NOT EXISTS promotiondb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Grant all privileges to admin user on Product database
GRANT ALL PRIVILEGES ON productdb.* TO 'admin'@'%';

-- Grant all privileges to admin user on Promotion database
GRANT ALL PRIVILEGES ON promotiondb.* TO 'admin'@'%';

-- Flush privileges to ensure changes take effect
FLUSH PRIVILEGES;

-- Display completion message
SELECT '================================================' AS '';
SELECT 'MySQL Database Initialization Completed!' AS '';
SELECT '================================================' AS '';
SELECT 'Created databases:' AS '';
SELECT '  - productdb (Product Service)' AS '';
SELECT '  - promotiondb (Promotion Service)' AS '';
SELECT '================================================' AS '';
SELECT 'Character Set: utf8mb4' AS '';
SELECT 'Collation: utf8mb4_unicode_ci' AS '';
SELECT 'Admin user granted all privileges' AS '';
SELECT '================================================' AS '';
