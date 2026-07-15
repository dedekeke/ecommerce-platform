-- ============================================
-- Promotion Service — Loyalty consumer idempotency ledger
-- ============================================
-- Consumer-side dedup for the loyalty `order.completed` consumer. The
-- order-service transactional-outbox relay (PR#112) delivers at-least-once
-- with a stable `outbox-event-id` per event; this table lets the consumer
-- drop redeliveries so a user's lifetime spend (loyalty tier) is not
-- double-counted. The PRIMARY KEY provides the uniqueness guarantee — a
-- concurrent duplicate insert (e.g. two service replicas) fails the second
-- transaction and rolls back its spend increment.

CREATE TABLE promotion_processed_loyalty_event (
    event_id    VARCHAR(100) NOT NULL PRIMARY KEY,
    topic       VARCHAR(64)  NOT NULL,
    consumed_at TIMESTAMP    NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
