-- ============================================
-- V4 — Orders archive table (2026-04-29)
-- ============================================
-- Cold storage for orders older than the retention threshold (default 365
-- days, see `order.archival.cutoff-days`). The OrderArchivalScheduler moves
-- rows out of `orders` into `orders_archive` nightly so the hot table stays
-- lean — partitioning kicks in only at scale (~50M rows; see
-- docs/PARTITIONING.md). Until then, archival alone keeps query plans fast.
--
-- Schema mirrors the live `orders` table 1:1 plus an `archived_at` audit
-- column. Foreign-key constraints to order_items are intentionally NOT
-- replicated — we copy the rows but cut the relationship so the live FK
-- can be dropped on archive (cascade delete moves children too).
--
-- The Hibernate `ddl-auto: update` strategy currently manages the live
-- `orders` schema; this file is the authoritative source for the archive
-- table because no JPA entity owns it (archival is SQL-only). Until
-- order-service adopts Flyway as its primary schema tool (planned but not
-- yet done — see docs/PARTITIONING.md), apply this DDL manually during
-- the rollout that flips `order.archival.enabled=true`. The file follows
-- the V<n>__*.sql Flyway naming convention so adoption is mechanical: just
-- enable the Flyway starter and the existing V1–V4 files become live
-- migrations on next deployment.
--
-- Postgres dialect.

CREATE TABLE IF NOT EXISTS orders_archive (
    id                 VARCHAR(36)   PRIMARY KEY,
    order_number       VARCHAR(255)  NOT NULL,
    user_id            VARCHAR(255)  NOT NULL,
    subtotal           NUMERIC(10,2) NOT NULL,
    tax                NUMERIC(10,2) NOT NULL,
    shipping_cost      NUMERIC(10,2) NOT NULL,
    total              NUMERIC(10,2) NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    payment_intent_id  VARCHAR(255),
    promotion_code     VARCHAR(255),
    discount_amount    NUMERIC(10,2),
    -- Embedded shipping address columns (keep names aligned with the live
    -- entity's @Embedded mapping — Address uses default field names).
    street             VARCHAR(255),
    city               VARCHAR(255),
    state              VARCHAR(255),
    zip_code           VARCHAR(255),
    country            VARCHAR(255),
    created_at         TIMESTAMP     NOT NULL,
    updated_at         TIMESTAMP     NOT NULL,
    archived_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Drives lookups by user during support requests on cold data.
CREATE INDEX IF NOT EXISTS idx_orders_archive_user
    ON orders_archive (user_id);

-- Drives time-window analytics queries that span both live and archive.
CREATE INDEX IF NOT EXISTS idx_orders_archive_created
    ON orders_archive (created_at);

-- Drives the archive uniqueness check used by the scheduler's idempotency
-- guard (WHERE NOT EXISTS).
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_archive_order_number
    ON orders_archive (order_number);
