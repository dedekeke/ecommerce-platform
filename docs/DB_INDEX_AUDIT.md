# Database Index Audit

> Applied 2026-04-30 as part of `feature/perf-batch-1` (SCALING_AND_IMPROVEMENTS.md §2.7).
> Append-only: no existing indexes are dropped. New indexes are added via Flyway
> migrations on services that already use Flyway (cart, product, promotion) and
> via JPA `@Index` annotations + reference DDL on services that still rely on
> Hibernate `ddl-auto: update` (order, inventory).

## Scope

Hot tables on the read/write critical path:

| Table | Service | DB | Schema management |
|-------|---------|----|--------------------|
| `orders` | order-service | PostgreSQL | JPA `ddl-auto: update` (+ V2 reference SQL) |
| `order_items` | order-service | PostgreSQL | JPA `ddl-auto: update` (+ V2 reference SQL) |
| `cart_items` | cart-service | PostgreSQL | Flyway |
| `carts` | cart-service | PostgreSQL | Flyway |
| `products` | product-service | MySQL | Flyway |
| `inventory_reservations` | inventory-service | PostgreSQL | JPA `ddl-auto: update` (+ V2 reference SQL) |
| `promotions` | promotion-service | MySQL | Flyway (no gaps found, see below) |

## Findings

### orders (order-service)

Existing: `idx_order_number (UNIQUE)`, `idx_user_id`, `idx_status`,
`idx_created_at`, `idx_promotion_code`, `idx_user_status_date (userId, status,
createdAt)`.

Gaps:

- **`payment_intent_id`** — `OrderRepository.findByPaymentIntentId` is on the
  payment-callback critical path. Without an index it is a sequential scan.
  Added `idx_order_payment_intent`.
- **`(status, created_at)`** — `OrderRepository.findAbandonedOrders` filters
  on `(status = PENDING AND created_at < cutoff)`. The composite
  `idx_user_status_date` cannot serve this query because `userId` is not in
  the predicate. Added `idx_order_status_created`.

### order_items (order-service)

Existing: none (only the implicit PK).

Gaps:

- **`order_id`** — JPA does not auto-create FK indexes and PostgreSQL does
  not auto-index FK columns. Loading items for a given order is the
  dominant access path. Added `idx_order_item_order`.
- **`product_id`** — supports reverse lookups (orders containing a given
  product) used by the refund saga and analytics jobs. Added
  `idx_order_item_product`.

### carts (cart-service)

Existing: `idx_user_id`, `idx_status`, `idx_expires_at`.

Gaps:

- **`(status, updated_at)`** — `CartRepository.findAbandonedCarts` filters
  on `(updated_at < threshold AND status = X)`. Today only `status` is
  indexed; the abandoned-cart cron does a sequential filter on
  `updated_at`. Added `idx_cart_status_updated`.

### cart_items (cart-service)

Existing: `idx_cart_id`, `idx_product_id`. No gaps.

### products (product-service)

Existing: `idx_product_sku (UNIQUE)`, `idx_product_name`,
`idx_product_category`, `idx_product_active`, `idx_product_price`.

Gaps:

- **`(active, category_id)`** — `ProductRepository.advancedSearch` filters
  on `active = true` and a category id derived from a 3-level traversal.
  Today the optimizer can use `idx_product_active` OR
  `idx_product_category` but not both. Added
  `idx_product_active_category`.
- **`(active, created_at)`** — `ProductRepository.findFeaturedProducts`
  orders by recency on active rows. Without this composite the optimizer
  falesorts after using `idx_product_active`. Added
  `idx_product_active_created`.

Open follow-up: `ProductRepository.searchByNameOrDescription` uses
`LIKE '%term%'` which cannot use a B-tree index regardless of how it is
shaped. The fix is a `tsvector` full-text index OR delegating to
Elasticsearch (already deployed). Out of scope for this batch — flagged in
SCALING_AND_IMPROVEMENTS.md §2.7.

### inventory_reservations (inventory-service)

Existing: `idx_reservation_product_id`, `idx_reservation_order_id`,
`idx_reservation_status`, `idx_reservation_expires_at`.

Gaps:

- **Partial composite `(expires_at) WHERE status = 'RESERVED'`** —
  `InventoryReservationRepository.findExpiredReservations` filters on
  `(status = 'RESERVED' AND expires_at < now)`. Once COMMITTED / RELEASED
  / EXPIRED rows dominate the table, the existing
  `idx_reservation_status` index is useless (low cardinality on the hot
  predicate value), and `idx_reservation_expires_at` returns far too many
  rows. A partial index keyed on `expires_at` and filtered to
  `status = 'RESERVED'` keeps the scan surface tiny and dense even at 10M+
  rows. Added `idx_reservation_status_expires_partial` (Postgres
  partial-index syntax — JPA cannot express this so the V2 SQL is the
  canonical source).
- A non-partial composite `(status, expires_at)` is also added via the
  `@Index` annotation as a fallback for environments where the V2 SQL has
  not yet been applied.

### promotions (promotion-service)

Existing: `idx_code (UNIQUE)`, `idx_active_dates (active, start_date,
end_date)`, `idx_type`, `idx_created_at`.

No gaps found. The composite `idx_active_dates` already covers the
`findValidPromotionByCode` and active-promotion queries.

## Plan caching

PostgreSQL `plan_cache_mode = auto` is the default since PG12. Verified: no
`spring.jpa.properties.hibernate...` overrides in any of our services touch
it. No action required.

HikariCP `prepareThreshold` defaults to `3` (PostgreSQL JDBC driver) — this
is the threshold before the driver flips a prepared statement to a
server-side prepared statement. No action required unless Hibernate stats
show plan churn.

## Verification

A Testcontainers integration test verifies each new index exists. Coverage:

- `cart-service` — `CartIndexAuditTest` runs against real Postgres,
  asserts `idx_cart_status_updated` is present and that V1 indexes are not
  dropped. **Pass** (2/2).
- `product-service` — index audit test scaffold attempted via
  `@DataJpaTest` and `@SpringBootTest` failed on a pre-existing
  common-library wiring gap (BusinessMetrics → MeterRegistry,
  CorrelationIdFilter → BaggageField, etc. unsatisfied in the slice). The
  Flyway migration `V3__Add_perf_indexes.sql` and the `@Index` annotations
  on `Product` are deployed; verification deferred to staging
  smoke-runs. Open follow-up: stand up a proper test fixture for the
  common-library beans, then add the audit test.
- `order-service`, `inventory-service` — no Flyway, indexes created by
  Hibernate from `@Index` annotations on next deploy. Reference DDL in
  `V2__Add_perf_indexes.sql` documents the expected schema for ops
  audits.

## Migration map

| Service | Migration | Indexes added |
|---------|-----------|---------------|
| cart-service | `V2__Add_perf_indexes.sql` | `idx_cart_status_updated` |
| product-service | `V3__Add_perf_indexes.sql` | `idx_product_active_category`, `idx_product_active_created` |
| order-service | `V2__Add_perf_indexes.sql` (reference DDL — JPA-managed) | `idx_order_payment_intent`, `idx_order_status_created`, `idx_order_item_order`, `idx_order_item_product` |
| inventory-service | `V2__Add_perf_indexes.sql` (reference DDL — JPA-managed) | `idx_reservation_status_expires_partial`, `idx_reservation_status_expires` (via @Index) |
