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
platform can be rolled out via Kustomize (`k8s/`) or Helm (`helm/`), and some services
carry both an HPA and a KEDA `ScaledObject`. The budget uses the highest ceiling so it
holds regardless of which path/autoscaler wins.

| Service | k8s HPA (`k8s/base/hpa.yaml`) | k8s KEDA (`k8s/base/scaling/*`) | Helm HPA (`helm/.../values-prod.yaml`) | **Ceiling used** |
|---------|------------------------------|--------------------------------|----------------------------------------|------------------|
| order     | max 8 | max 4 | max 8 | **8** |
| payment   | max 8 | max 4 | max 8 | **8** |
| product   | max 8 | —     | max 8 | **8** |
| inventory | —     | max 4 | fixed 2 (`replicaCount`) | **4** |
| user      | —     | —     | fixed 2 | **2** |
| cart      | —     | —     | fixed 2 | **2** |
| promotion | —     | —     | fixed 2 | **2** |

Notes:
- **order and payment carry two autoscalers** (a CPU HPA at max 8 in `k8s/base/hpa.yaml`
  plus a Kafka/outbox-lag KEDA `ScaledObject` at max 4). That is a config smell worth
  reconciling, but for budgeting we assume the higher ceiling (8).
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
| order-service     | 8 | 2 | 8 | 64 |
| payment-service   | 5 | 2 | 8 | 40 |
| inventory-service | 5 | 2 | 4 | 20 |
| user-service      | 5 | 2 | 2 | 10 |
| cart-service      | 5 | 2 | 2 | 10 |
| **Subtotal (app)**|   |   |   | **144** |
| Admin / migration / backup / superuser reserve | | | | 10 |
| **Total required** | | | | **154** |
| **Server ceiling (`POSTGRES_MAX_CONNECTIONS`)** | | | | **200** |
| Headroom | | | | 46 |

order-service gets the largest pool (8): it is the checkout orchestrator, holding a DB
connection across synchronous gRPC calls to cart/payment/inventory during the saga.

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
- **Pool sizes** are Spring placeholders with per-service defaults, e.g.
  `maximum-pool-size: ${DB_POOL_SIZE:8}` and `minimum-idle: ${DB_POOL_MIN_IDLE:2}`, in each
  service's base `application.yml` (cart uses its `docker`/`local`/`prod` profiles, since
  its datasource lives there). Because the `prod` profiles do not override the datasource,
  the base/profile defaults apply in production too. Ops can override without a rebuild by
  setting `DB_POOL_SIZE` / `DB_POOL_MIN_IDLE` in a service's `environment:` (per-service) or
  in `.env` (applies to all services that load `.env` — use with care).
- `minimum-idle` was lowered from 5 to 2 so a large replica count does not pin a big
  baseline of idle connections on the shared instance.

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
If order/payment's dual HPA+KEDA autoscalers are reconciled to a single ceiling, update
the replica table above.
