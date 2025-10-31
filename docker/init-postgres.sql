-- ============================================
-- PostgreSQL Database Initialization Script
-- E-Commerce Platform - Transactional Services
-- ============================================
-- This script creates databases for services requiring strong ACID compliance
-- Services: User, Order, Payment, Inventory
-- ============================================

-- Create User Service Database
CREATE DATABASE userdb;
COMMENT ON DATABASE userdb IS 'User Service - User management and profiles';

-- Create Order Service Database
CREATE DATABASE orderdb;
COMMENT ON DATABASE orderdb IS 'Order Service - Order processing and management';

-- Create Payment Service Database
CREATE DATABASE paymentdb;
COMMENT ON DATABASE paymentdb IS 'Payment Service - Payment processing and transactions';

-- Create Inventory Service Database
CREATE DATABASE inventorydb;
COMMENT ON DATABASE inventorydb IS 'Inventory Service - Stock management and reservations';

-- Grant privileges (databases are owned by POSTGRES_USER)
\echo '================================================'
\echo 'PostgreSQL Database Initialization Completed!'
\echo '================================================'
\echo 'Created databases:'
\echo '  - userdb (User Service)'
\echo '  - orderdb (Order Service)'
\echo '  - paymentdb (Payment Service)'
\echo '  - inventorydb (Inventory Service)'
\echo '================================================'
\echo 'All databases owned by user: admin'
\echo '================================================'
