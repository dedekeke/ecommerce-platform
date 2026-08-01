# Database Connection Budget

Problem (Lore 4779aa7a, Scalability P1): the platform runs **one shared PostgreSQL**
and **one shared MySQL** instance, each fronting several services. Every SQL service
ships Hikari `maximum-pool-size: 20`. Once services scale out horizontally, the sum of
pools exceeds the DB server's `max_connections`, causing `HikariPool` acquisition
timeouts and cascading HTTP 500s. Example before this change: order-service alone at its
autoscaler ceiling = 8 replicas x 20 = 160 connections, versus PostgreSQL's default
`max_connections` of 100.

This doc defines the connection budget, the right-sized pools, and the explicit
server-side ceilings that keep worst-case demand inside the DB limits.

## Rule

For each shared DB instance:

```
sum over co-tenant services of ( hikari.maximum-pool-size x max_replicas )
  + admin_reserve (~10 for migrations / psql / monitoring / backups / superuser)
  <= server max_connections
```

## Topology (docker-compose.yml and docker-compose.prod.yml)

| DB instance | Engine | Co-tenant services |
|-------------|--------|--------------------|
| `postgres`  | PostgreSQL 16 | user, cart, order, payment, inventory |
| `mysql`     | MySQL 8.2 | product, promotion |
| `mongodb`   | MongoDB 7 | notification, media (document store, no JDBC pool — out of scope) |

## Worst-case replica ceilings (from the real manifests)

Replica ceilings are taken as the **maximum across every deployment path**, because the
platform can be rolled out via Kustomize (`k8s/`) or Helm (`helm/`). The budget uses the
highest ceiling so it holds regardless of which path/autoscaler wins.

> Updated 2026-07-14 (PR #107): the order/payment dual-autoscaler smell flagged in the
> original version of this doc is resolved — their CPU HPAs were removed and KEDA is the
> single autoscaler, with `maxReplicaCount` raised to 8 (matching this budget). PR #107
> also added CPU HPAs for api-gateway (not a SQL co-tenant), cart, and user — the latter
> two pinned to max 2 to honour this budget.

| Service | k8s HPA (`k8s/base/hpa.yaml`) | k8s KEDA (`k8s/base/scaling/*`) | Helm HPA (`helm/.../values-prod.yaml`) | **Ceiling used** |
|---------|------------------------------|--------------------------------|----------------------------------------|------------------|
| order     | — (removed, PR #107) | max 8 | max 8 | **8** |
| payment   | — (removed, PR #107) | max 8 | max 8 | **8** |
| product   | max 8 | —     | max 8 | **8** |
| inventory | —     | max 4 | fixed 2 (`replicaCount`) | **4** |
| user      | max 2 | —     | fixed 2 | **2** |
| cart      | max 2 | —     | fixed 2 | **2** |
| promotion | —     | —     | fixed 2 | **2** |

Notes:
- search / notification / recommendation KEDA objects exist but those services are not
  SQL co-tenants (Elasticsearch / MongoDB), so they do not consume the pools above.
- In `k8s/` and `helm/`, **PostgreSQL and MySQL are external/managed** (wired via
  ExternalSecrets; no in-cluster StatefulSet). `max_connections` there must be provisioned
  on the managed instance to at least the "Total required" figures below. The compose
  files (dev + prod) run the DB containers, so we pin `max_connections` on them directly.

## PostgreSQL budget

`POSTGRES_MAX_CONNECTIONS = 200`

| Service | Pool (`DB_POOL_SIZE`) | min-idle | Max replicas | Worst-case conns |
|---------|-----------------------|----------|--------------|------------------|
| order-service     | 12 | 2 | 8 | 96 |
| payment-service   | 5 | 2 | 8 | 40 |
| inventory-service | 5 | 2 | 4 | 20 |
| user-service      | 5 | 2 | 2 | 10 |
| cart-service      | 5 | 2 | 2 | 10 |
| **Subtotal (app)**|   |   |   | **176** |
| Admin / migration / backup / superuser reserve | | | | 10 |
| **Total required** | | | | **186** |
| **Server ceiling (`POSTGRES_MAX_CONNECTIONS`)** | | | | **200** |
| Headroom | | | | 14 |

order-service gets the largest pool (12): `OrderCreationSaga` is `@Transactional` and
holds its DB connection across synchronous gRPC/REST calls to cart, promotion, payment
and inventory plus a Kafka publish — the connection is pinned for the entire saga, so a
thin pool would serialize checkouts under load.

> **12 is a deliberately conservative default, not yet load-verified.** The CPU-based HPA
> will not scale out on connection-bound saturation (a saga blocked waiting for a pool
> permit is not CPU-hot), so we err on the side of a larger pool. The correct value should
> be confirmed by the k6 load-test follow-up; until then `DB_POOL_SIZE` is env-overridable
> for tuning without a rebuild. If it is later raised, re-check the subtotal below — at
> pool 16 the Postgres subtotal would be 176 + 4x8 = 208, exceeding the 200 ceiling, so
> `POSTGRES_MAX_CONNECTIONS` must be raised in lockstep.

### KEDA scalers open connections outside Hikari

The `inventory-service` and `payment-service` KEDA `ScaledObject`s use the **PostgreSQL
scaler** (`inventory-service-outbox-lag`, `payment-service-outbox-lag`) to poll the outbox
table. Each polling connection is opened by KEDA directly, **not** through the service's
Hikari pool, so it is not counted in the pool math above. These are few and short-lived,
but they draw from the same `max_connections` — the ~10 admin/monitoring reserve is sized
to absorb them. If KEDA polling frequency or the number of postgres-scaler triggers grows,
increase the reserve accordingly.

## MySQL budget

`MYSQL_MAX_CONNECTIONS = 150`

| Service | Pool (`DB_POOL_SIZE`) | min-idle | Max replicas | Worst-case conns |
|---------|-----------------------|----------|--------------|------------------|
| product-service   | 8 | 2 | 8 | 64 |
| promotion-service | 5 | 2 | 2 | 10 |
| **Subtotal (app)**|   |   |   | **74** |
| Admin / migration / backup reserve | | | | 10 |
| **Total required** | | | | **84** |
| **Server ceiling (`MYSQL_MAX_CONNECTIONS`)** | | | | **150** |
| Headroom | | | | 66 |

product-service is read-heavy but fronted by Redis + Caffeine caches, so a pool of 8 is
comfortable. MySQL's own default is 151; 150 keeps the intent explicit and env-driven.

## How the knobs are wired

- **Server ceilings** are set in both `docker-compose.yml` and `docker-compose.prod.yml`,
  routed through `.env` (`POSTGRES_MAX_CONNECTIONS`, `MYSQL_MAX_CONNECTIONS`) per project
  rules — no hardcoded values.
  Postgres: `command: ["postgres","-c","max_connections=${POSTGRES_MAX_CONNECTIONS}"]`.
  MySQL: `--max-connections=${MYSQL_MAX_CONNECTIONS}`.
  For k8s/helm (managed DBs), provision the managed instance `max_connections` to at
  least the Total-required figures above.
- **Pool sizes** are Spring placeholders with per-service defaults, e.g. order uses
  `maximum-pool-size: ${DB_POOL_SIZE:12}`, product `${DB_POOL_SIZE:8}`, the rest
  `${DB_POOL_SIZE:5}`, all with `minimum-idle: ${DB_POOL_MIN_IDLE:2}`, in each service's
  base `application.yml` (cart uses its `docker`/`local`/`prod` profiles, since its
  datasource lives there). Because the `prod` profiles do not override the datasource, the
  base/profile defaults apply in production too. Ops can override without a rebuild by
  setting `DB_POOL_SIZE` / `DB_POOL_MIN_IDLE` in a service's `environment:` (per-service).
  Setting them in an env file applies to every service that loads that file — in **dev**
  compose (`docker-compose.yml`, `env_file: .env`) that is all services via `.env`; in
  **prod** compose it is `production.env`. Prefer the per-service `environment:` override
  to keep the budget intact.
- `minimum-idle` was lowered from 5 to 2 so a large replica count does not pin a big
  baseline of idle connections on the shared instance.

> **Env-file triplication (drift risk).** The three env samples — `.env.example`,
> `.env.template`, `production.env.example` — now each carry `POSTGRES_MAX_CONNECTIONS` /
> `MYSQL_MAX_CONNECTIONS`. They can drift out of sync. Consolidating them into a single
> source of truth is a separate cleanup task (out of scope here); until then, update all
> three together when changing a ceiling.

## Monitoring (already wired — not built here)

HikariCP metrics are already scraped by Micrometer/Prometheus. `config/prometheus/alerts.yml`
already alerts on `hikaricp_connections_active / hikaricp_connections_max > 0.9`.

Recommended additional alert to catch pool starvation before it turns into 500s
(add to `config/prometheus/alerts.yml`, database_alerts group):

```yaml
- alert: HikariConnectionsPending
  expr: hikaricp_connections_pending > 0
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Threads waiting for a DB connection on {{ $labels.job }}"
    description: "hikaricp_connections_pending > 0 for 2m — pool too small or DB saturated."
```

## Changing the budget

If you raise a pool size, a service's max replicas, or add a new co-tenant, re-check the
subtotal against the server ceiling. If subtotal + reserve would exceed the ceiling,
either raise `*_MAX_CONNECTIONS` (and provision DB memory accordingly) or lower the pool.
