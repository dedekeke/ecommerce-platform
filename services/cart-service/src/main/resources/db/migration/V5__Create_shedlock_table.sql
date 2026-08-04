-- ============================================
-- V5 — ShedLock lock table
-- ============================================
-- Backs distributed locking for the @Scheduled jobs (abandoned-cart scan,
-- expired/abandoned cleanup) so exactly one instance runs each job when
-- cart-service is scaled to multiple replicas. Managed by the ShedLock
-- JdbcTemplateLockProvider (net.javacrumbs.shedlock) — column names and types
-- follow the provider's required schema.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
