-- ============================================
-- Promotion Service — Multi-Currency (§3.5)
-- ============================================
-- Static FX rates expressed as units-of-currency-per-USD. The CurrencyService
-- composes any from->to conversion via USD as a pivot:
--   amount_target = amount_source * (rate_target / rate_source)
-- USD itself has rate_to_usd = 1 (identity).
--
-- Rates are seed data — refreshed offline by ops, not by the application.

CREATE TABLE currency_rates (
    code         VARCHAR(3)     NOT NULL PRIMARY KEY,
    rate_to_usd  DECIMAL(12, 6) NOT NULL,
    updated_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_rate_to_usd_positive CHECK (rate_to_usd > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed rates (snapshot 2026-04-29 — refreshed by ops on a slow cadence).
INSERT INTO currency_rates (code, rate_to_usd) VALUES
    ('USD', 1.000000),
    ('EUR', 0.920000),
    ('GBP', 0.790000),
    ('JPY', 149.500000),
    ('VND', 24500.000000),
    ('CAD', 1.350000);
