-- ============================================
-- V9 — ShedLock lock table
-- ============================================
-- Backs distributed locking for the non-idempotent @Scheduled jobs
-- (abandoned-order cancel, daily sales report, cleanup, archival, subscription
-- poller, refund/RMA recovery) so exactly one instance runs each job when
-- order-service is scaled to multiple replicas. The outbox relay deliberately
-- does NOT use this table — it stays concurrent-safe via FOR UPDATE SKIP LOCKED.
--
-- Managed by the ShedLock JdbcTemplateLockProvider (net.javacrumbs.shedlock);
-- column names/types follow the provider's required schema.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
