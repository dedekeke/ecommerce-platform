-- Transactional Outbox table (canonical reference DDL).
-- The inventory-service currently relies on Hibernate `ddl-auto: update` to create
-- this schema from the OutboxEvent JPA entity. This file is the authoritative
-- contract for downstream tooling (Flyway adoption, ops audits, infra-as-code,
-- and future Debezium CDC connector configuration). Keep it in sync with
-- com.ecommerce.inventoryservice.outbox.OutboxEvent.
--
-- Postgres dialect.
--
-- Version note: V1 because inventory-service has no prior versioned migrations
-- under src/main/resources/db/migration. Mirrors order-service / payment-service.

CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL UNIQUE,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    topic           VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP    NULL,
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(1024) NULL
);

-- Drives the relay's claim query: WHERE published_at IS NULL ORDER BY created_at ASC.
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_event (published_at, created_at);

-- Supports per-aggregate audit queries during incident response.
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_event (aggregate_type, aggregate_id);
