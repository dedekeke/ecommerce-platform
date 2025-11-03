-- User Service Database Schema
-- Creates tables for users and addresses

-- Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    auth0_id VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN,
    picture_url VARCHAR(500),
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for users table
CREATE INDEX idx_auth0_id ON users(auth0_id);
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_role ON users(role);
CREATE INDEX idx_active ON users(active);
CREATE INDEX idx_last_login ON users(last_login_at);

-- User Addresses Table
CREATE TABLE user_addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL,
    street VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_billing BOOLEAN NOT NULL DEFAULT FALSE,
    recipient_name VARCHAR(200),
    recipient_phone VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for user_addresses table
CREATE INDEX idx_user_id ON user_addresses(user_id);
CREATE INDEX idx_user_default ON user_addresses(user_id, is_default);
CREATE INDEX idx_is_billing ON user_addresses(user_id, is_billing);

-- Comments for documentation
COMMENT ON TABLE users IS 'User profiles synchronized with Auth0';
COMMENT ON COLUMN users.auth0_id IS 'Auth0 user ID (sub claim from JWT)';
COMMENT ON COLUMN users.role IS 'User role: USER, ADMIN, SUPPORT, WAREHOUSE_MANAGER';
COMMENT ON COLUMN users.email_verified IS 'Email verification status from Auth0';
COMMENT ON COLUMN users.last_login_at IS 'Last login timestamp, updated on each login';

COMMENT ON TABLE user_addresses IS 'User shipping and billing addresses';
COMMENT ON COLUMN user_addresses.label IS 'Address label (e.g., Home, Work, Office)';
COMMENT ON COLUMN user_addresses.is_default IS 'Whether this is the default shipping address';
COMMENT ON COLUMN user_addresses.is_billing IS 'Whether this is a billing address';
COMMENT ON COLUMN user_addresses.country IS 'ISO 3166-1 alpha-2 country code';
