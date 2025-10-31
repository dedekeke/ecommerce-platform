-- PostgreSQL Database Initialization Script
-- Creates all databases required for the e-commerce platform microservices

-- User Service Database
CREATE DATABASE userdb;
GRANT ALL PRIVILEGES ON DATABASE userdb TO admin;

-- Product Service Database
CREATE DATABASE productdb;
GRANT ALL PRIVILEGES ON DATABASE productdb TO admin;

-- Order Service Database
CREATE DATABASE orderdb;
GRANT ALL PRIVILEGES ON DATABASE orderdb TO admin;

-- Payment Service Database
CREATE DATABASE paymentdb;
GRANT ALL PRIVILEGES ON DATABASE paymentdb TO admin;

-- Inventory Service Database
CREATE DATABASE inventorydb;
GRANT ALL PRIVILEGES ON DATABASE inventorydb TO admin;

-- Promotion Service Database
CREATE DATABASE promotiondb;
GRANT ALL PRIVILEGES ON DATABASE promotiondb TO admin;

-- Verify databases were created
\l
