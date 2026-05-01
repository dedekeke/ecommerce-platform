# Orders Table Partitioning Strategy

Last updated: 2026-04-29
Owner: order-service (backend)

## Why Partition?

The `orders` table is the dominant hot path for order-service: every checkout
writes a row, every order-history page read queries it, every nightly
analytics job scans it. As row count climbs:

- **Query latency degrades non-linearly** once indexes outgrow shared buffers
  (Postgres has to fetch heap pages from disk).
- **Index maintenance cost rises** — every INSERT/UPDATE has to update every
  index, and B-tree depth grows with `log(N)`.
- **VACUUM and ANALYZE windows lengthen** — table-level locks during
  autovacuum become a tail-latency problem.
- **Backups balloon** — pg_dump on a single 100 GB table is no fun.

Range-partitioning by `created_at` (monthly buckets) gives us:

- **Partition pruning** — queries with a `WHERE created_at >= …` clause only
  touch the relevant child partitions.
- **Cheap drops** — archiving "everything older than 18 months" becomes
  `DETACH PARTITION` + `DROP TABLE`, not a long-running DELETE.
- **Bounded VACUUM scope** — autovacuum runs per partition.

## When to Apply (the 50M row trigger)

Partitioning is **not free**. It adds operational overhead (rolling
partitions forward, monitoring per-partition bloat, more tables to alert on)
and the partition-key-must-be-in-PK constraint complicates application code.

We trigger the partition migration when **`orders` exceeds ~50M rows**, which
is roughly:

- The point at which the primary B-tree exceeds typical EC2 shared_buffers
  configurations (8–16 GB).
- The point at which `EXPLAIN ANALYZE` on `findByUserIdOrderByCreatedAtDesc`
  consistently reports buffer reads rather than hits.
- The point at which nightly analytics jobs (sales-report, abandonment scan)
  start exceeding their 30-minute SLO.

**Until then, the V4 archival job is the sole hot-table-trim mechanism.**
Archival runs nightly and moves orders older than `order.archival.cutoff-days`
(default 365) into `orders_archive` — keeping the live table well under the
50M threshold for years given current order volume.

Telemetry to watch (`promotion-service` Prometheus + Grafana dashboard):

- `pg_stat_user_tables{relname="orders"}.n_live_tup` — row count.
- `order.repository.findByUserId.p99_seconds` — query latency.
- `pg_stat_user_tables{relname="orders"}.last_vacuum` — VACUUM cadence.

## Pre-flight Checklist for Promoting the Template

The DDL lives in `db/migration/V_pending__partition_orders_table.sql.template`.
Promoting it to a live migration is a one-shot, downtime-bearing operation.

Before promoting:

- [ ] Confirm row count > 50M (or another documented justification).
- [ ] Schedule a maintenance window (≥30 min on a 50M-row table).
- [ ] Take a logical backup of `orders` and `order_items`.
- [ ] Run the partitioning DDL on a staging clone with production data
      volume; measure `INSERT … SELECT` duration.
- [ ] Replace `{{TODAY}}` / `{{FIRST_OF_M0}}` etc. tokens with concrete
      dates — Flyway runs SQL deterministically, so dynamic dates are unsafe.
- [ ] Plan a recurring partition-roll job (or adopt
      [pg_partman](https://github.com/pgpartman/pg_partman)) — the template
      seeds 12 months only.
- [ ] Confirm `OrderRepository` query methods filter on `created_at` where
      possible to enable partition pruning.
- [ ] Update `OrderRepository.findByOrderNumber` — order-number lookups
      cannot prune on `created_at` and must scan all partitions; consider
      adding `created_at` as a query parameter at callsites that already
      have it (payment callbacks, support tooling).
- [ ] Rename the file to `V<N>__Partition_orders_table.sql` and let Flyway
      apply it.
- [ ] Soak for 24h with feature flags ready to roll back.
- [ ] After 24h, drop the legacy `orders_legacy` table.

## Archival Strategy

The Flyway V4 migration creates `orders_archive` with the same column set as
`orders` plus an `archived_at` audit timestamp.

`OrderArchivalScheduler` runs daily at 04:00 UTC and:

1. Computes `cutoffDate = now() - order.archival.cutoff-days` (default 365).
2. Queries `orders` for IDs created before the cutoff, ordered by
   `created_at` ascending, in batches of 1000.
3. For each batch, INSERTs into `orders_archive` only when the row does not
   already exist (`WHERE NOT EXISTS … order_number = ?`) — making the job
   idempotent on re-run / partial failure.
4. DELETEs the same IDs from the live `orders` table.
5. Steps 3–4 run inside a single transaction per batch — partial failure
   leaves the data either fully archived or fully live, never split.

Configuration:

```yaml
order:
  archival:
    cutoff-days: 365              # rows older than this are eligible
    batch-size:  1000             # rows per transaction
    cron:        "0 0 4 * * ?"    # daily at 04:00 UTC
    enabled:     true
```

Disabling: set `order.archival.enabled=false` (no-ops the scheduler bean).

## Citations

- PostgreSQL docs — [Table Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html).
- Brandur Leach — [Partitioning Postgres in Production](https://brandur.org/postgres-partitioning).
- pg_partman — partition lifecycle management we expect to adopt at promotion time.
- Internal: `docs/DB_INDEX_AUDIT.md` — index strategy (V2/V3 migrations).
- Internal: `docs/BACKUP_AND_DR.md` — backup procedure used in pre-flight step.
