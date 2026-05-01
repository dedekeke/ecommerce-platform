# Scaling and Improvement Roadmap

> Evidence-based roadmap for the next 6–12 months.  
> Platform: Spring Boot 3.2 / Java 21 · 11 microservices · 5 datastores · React + Angular MFEs · Kubernetes / Helm  
> Last updated: 2026-04-29

---

## Top 5 to Do Next (Impact / Effort)

| # | Item | Why now | Effort |
|---|------|---------|--------|
| 1 | **Outbox pattern via Debezium CDC** (§4) | Eliminates the active dual-write bug class in `order-service` and `payment-service`; highest correctness ROI with zero application-layer changes after setup. | M |
| 2 | **StructuredTaskScope fan-out in OrderService** (§2) | Virtual threads are already on; `OrderCreationSaga` makes ≥3 sequential gRPC calls that can run in parallel, directly cutting p95 order creation latency. | S |
| 3 | **HikariCP pool right-sizing** (§2) | Default pool size of 10 is almost certainly wrong for virtual-thread workloads at scale; a misconfigured pool is a silent p99 killer. Takes an afternoon. | S |
| 4 | **KEDA Kafka consumer lag autoscaling** (§1) | Standard HPA cannot see Kafka lag; `search-service` and `notification-service` consumers will stall under burst load without this. | M |
| 5 | **SLO definitions + error budgets per service** (§5) | No SLO means no objective basis for prioritising reliability vs. feature work; blocks error budget policy and chaos engineering programmes. | S |

---

## 1. Scale

### 1.1 Read Scale

#### Postgres / MySQL Read Replicas

**What.** Add streaming hot-standby replicas for `user-service` (PostgreSQL) and `product-service` / `order-service` (MySQL). Route all `SELECT` traffic through a secondary datasource in Spring using `@Transactional(readOnly = true)` + `AbstractRoutingDataSource`.

**Why.** At high concurrency the primary becomes the bottleneck for reads that do not mutate state. Read replicas offload 60–80 % of queries on typical OLTP systems.

**Trigger.** Primary CPU sustained > 70 % during read-heavy periods, OR p95 `SELECT` latency > 200 ms, OR HikariCP `pendingThreads` > 5 for more than 30 s.

**Effort.** M — replica provisioning is infrastructure work; `AbstractRoutingDataSource` routing is ~50 lines of Spring config.

**Risk if skipped.** Primary saturation during flash sales or product listing pages will cause cascading slowdowns across every service sharing the DB host.

**References.**
- PostgreSQL hot standby official documentation: `https://www.postgresql.org/docs/current/hot-standby.html` (retrieved 2026-04-29) — explains WAL streaming, replication lag, and `hot_standby_feedback`.
- Spring `AbstractRoutingDataSource` pattern is documented in Spring Data reference docs at `https://docs.spring.io/spring-data/jpa/reference/`.

---

#### CQRS Read Models

**What.** For `product-service` and `order-service`, project write-model events onto denormalised read models stored in MongoDB (for flexible schema) or as materialised SQL views. The read side is updated asynchronously via Kafka events.

**Why.** CQRS separates the update path from the query path, allowing independent optimisation. Product list pages need flat, pre-joined documents; the normalised write model does not serve that shape efficiently at scale.

**Trigger.** p95 product list response > 300 ms after caching layer is saturated, OR `advancedSearch` query plan shows full-table scans despite indexes, OR cache hit rate drops below 85 % under high write-invalidation churn.

**Effort.** L — requires event schema discipline, a projection worker, and operational ownership of a second data store per bounded context.

**Risk if skipped.** The `ProductRepository.advancedSearch` query (which traverses a 3-level category parent chain via JPQL joins) will not scale beyond moderate catalogue sizes without a flat read projection.

**References.**
- Martin Fowler, *CQRS*, `https://martinfowler.com/bliki/CQRS.html` (retrieved 2026-04-29) — recommends restricting CQRS to bounded contexts with genuine read/write imbalance.

---

#### Elasticsearch Projection Patterns

**What.** Ensure the `search-service` Elasticsearch index is populated exclusively from Kafka events, not synchronous writes. Add an index-lifecycle-management (ILM) rollover policy for the product index. Use `_source: false` on fields that are only used for filtering, not display.

**Why.** Direct synchronous indexing couples write latency to ES availability. Event-driven projection decouples them and makes the index eventually consistent in a controlled way.

**Trigger.** Indexing p99 > 500 ms, OR ES cluster health turns yellow more than once per week, OR product write throughput exceeds 500 ops/s.

**Effort.** M

**Risk if skipped.** A slow or unavailable Elasticsearch node will block product updates in the product service if indexing is synchronous.

**References.**
- Elasticsearch shard sizing guide: `https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html` (retrieved 2026-04-29) — recommends 10–50 GB per shard, ≤ 200 M docs.

---

### 1.2 Write Scale

#### Orders Table Partitioning

**What.** Range-partition the `orders` table by `created_at` (monthly partitions). Use PostgreSQL native declarative partitioning. Detach and archive old partitions rather than deleting rows.

**Why.** As order volume grows, unbounded tables cause index bloat and vacuum pressure. Range partitioning keeps the hot working set in cache and reduces vacuum scope.

**Trigger.** `orders` table > 50 M rows, OR VACUUM runtime exceeds 10 min, OR index bloat ratio (measured via `pgstattuple`) > 30 %.

**Effort.** M — requires a migration that recreates the table as partitioned; `pg_partman` automates future partition creation.

**Risk if skipped.** Dead-tuple accumulation and runaway autovacuum will cause write amplification and unpredictable p99 spikes at scale.

**References.**
- PostgreSQL Partitioning docs: `https://www.postgresql.org/docs/current/ddl-partitioning.html`.

---

#### Inventory Reservations Partitioning + Archival

**What.** The `inventory_reservations` table has `COMMITTED`, `RELEASED`, and `EXPIRED` rows that are never read again after state change. Partition by `status + created_at` or add a background archival job that moves terminal rows to a cold table after 30 days.

**Why.** The `findExpiredReservations` query currently scans the entire table. With high order volume, terminal reservations will dominate the table and degrade this scheduled scan.

**Trigger.** `inventory_reservations` row count > 10 M, OR `findExpiredReservations` query time > 2 s, OR the scheduled cleanup job runs longer than its cron interval.

**Effort.** S (archival job) → M (full partitioning)

**Risk if skipped.** The reservation expiry cron will progressively fall behind, causing stale reservations to hold phantom stock.

**References.**
- The `@Index` annotations on `idx_reservation_status` and `idx_reservation_expires_at` in `InventoryReservation.java` show the existing indexing intent; partitioning is the next logical step when rows exceed tens of millions.

---

### 1.3 Cache Scale

#### Redis Cluster (Horizontal Sharding)

**What.** Migrate from single-node Redis (with optional Sentinel HA) to Redis Cluster (minimum 3 primary + 3 replica nodes) when the dataset outgrows a single instance. Use hash tags `{product:123}` for commands that span multiple keys.

**Why.** Redis Cluster provides automatic data sharding across 16,384 hash slots, enabling horizontal throughput scaling that Sentinel cannot provide.

**Trigger.** Redis memory usage > 70 % of host RAM, OR Redis commands/sec > 80,000, OR replication lag on Sentinel primary > 500 ms under write bursts.

**Effort.** M — client-side changes (Lettuce/Jedis cluster mode) + ops work.

**Risk if skipped.** A single-node Redis will become a memory and throughput ceiling for the entire caching layer.

**References.**
- Redis Cluster overview: `https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/` (retrieved 2026-04-29) — minimum 3 masters; strong vs. eventual consistency trade-off documented.

---

#### Multi-Tier Caching: L1 Caffeine + L2 Redis

**What.** Add an in-process Caffeine L1 cache in front of the Redis L2 cache for the hottest read paths: `products/{id}`, `categories`, `active-promotions`. Use Spring Cache `@Cacheable` layered with a custom `CompositeCacheManager`.

**Why.** A Redis round-trip costs 0.5–2 ms. At 10k rps on a product detail endpoint, eliminating 90 % of Redis calls with a 100 ms in-process cache reduces external calls by ~900 per second per pod.

**Trigger.** Redis command rate > 50,000/s, OR Redis p99 > 5 ms, OR product-service CPU profile shows > 10 % time in serialisation/deserialisation of cached objects.

**Effort.** S — Caffeine is already a transitive dependency of Spring Boot; the `CompositeCacheManager` pattern is well-documented.

**Risk if skipped.** Redis becomes a synchronous bottleneck at high pod concurrency even though it is fast.

**References.**
- Caffeine: `https://github.com/ben-manes/caffeine` — near-optimal hit rate with Window TinyLFU policy.

---

#### Cache Stampede Mitigation

**What.** Implement probabilistic early recomputation (PER / "jitter + lock") for high-contention keys like `popular-products` and `category-tree`. Add TTL jitter (±10–15 % of base TTL) to all cache entries to avoid synchronised mass expiry. Use a Redis `SETNX`-based distributed lock as a single-flight guard for cache-miss database fetches.

**Why.** When a popular cache key expires under concurrent load, hundreds of threads simultaneously miss and hammer the DB. TTL jitter spreads expiry; single-flight ensures only one thread reconstructs the value.

**Trigger.** Database `slow_query_log` shows repeated spikes at regular intervals matching cache TTLs, OR cache hit rate drops suddenly below 80 % during traffic spikes.

**Effort.** S

**Risk if skipped.** Flash sales and promotional events — exactly the scenarios this platform is built for — will cause periodic DB overload when campaign-related cache keys expire simultaneously.

**References.**
- Redis documentation on cache patterns: `https://redis.io/docs/latest/develop/use/patterns/`.

---

### 1.4 Search Scale

#### Elasticsearch Shard Sizing and ILM

**What.** Establish a target shard size of 20–40 GB for the product index. Configure ILM with rollover thresholds (`max_primary_shard_size: 40gb`, `max_age: 30d`). Use `_shrink` API on read-only historical indices to reduce shard count.

**Why.** Oversharding degrades search performance because each shard consumes a search thread. Undersizing creates 2 B-document hard limits. ILM automates growth without manual intervention.

**Trigger.** Any single shard > 50 GB, OR cluster-wide shard count > 1,000, OR search p99 > 500 ms despite reasonable query complexity.

**Effort.** S (ILM policy) → M (migration if existing shards are wrong-sized)

**Risk if skipped.** Unbounded index growth without ILM is a well-known cause of Elasticsearch cluster instability in production.

**References.**
- Elasticsearch shard sizing: `https://www.elastic.co/guide/en/elasticsearch/reference/current/size-your-shards.html` (retrieved 2026-04-29).

---

#### Hot-Warm-Cold Data Tiers

**What.** Assign product index to hot tier (SSD-backed data nodes). Move indices older than 90 days to warm tier (less expensive storage). Add a cold tier for archival. Configure with ILM `allocate` action and node attributes `node.roles: [data_hot]`.

**Why.** Separates I/O-intensive active search from low-cost historical data storage, reducing hardware costs at scale.

**Trigger.** Elasticsearch data node storage > 60 % full, OR index total size > 500 GB.

**Effort.** M

**Risk if skipped.** All nodes are loaded equally whether serving active queries or storing 3-year-old product data.

**References.**
- Elasticsearch data tiers: `https://www.elastic.co/guide/en/elasticsearch/reference/current/data-tiers.html`.

---

#### Learning-to-Rank (ML Ranking)

**What.** Add Elasticsearch Learning to Rank (LTR) as a second-stage rescorer. Train a LambdaMART model (XGBoost) on click / purchase signals extracted from Kafka events. Features: BM25 score, product price, stock level, days-since-update, category match.

**Why.** Keyword relevance alone does not capture revenue intent. LTR can significantly improve conversion on the search-results page.

**Trigger.** Click-through rate on search results < 15 %, OR add-to-cart from search < 5 %, OR A/B test shows BM25-only ranking underperforms.

**Effort.** L — requires labelled training data (judgment lists), external model training pipeline, and Elasticsearch 8.12 + with appropriate licence.

**Risk if skipped.** Platform remains competitive only on keyword match quality; personalised ranking is a table-stakes feature for mid-market e-commerce.

**References.**
- Elasticsearch LTR documentation: `https://www.elastic.co/guide/en/elasticsearch/reference/current/learning-to-rank.html` (retrieved 2026-04-29) — requires ES 8.12+; GBDT inference is built-in.

---

### 1.5 Compute Scale

#### HPA Tuning

**What.** Replace default CPU-only HPA with multi-metric HPA (CPU + custom Prometheus metric `http_server_requests_seconds_count`). Set `scaleDown.stabilizationWindowSeconds: 300` and `scaleUp.policies` with both `Percent: 100` and `Pods: 4` policies to limit burst scaling speed.

**Why.** CPU alone is a lagging indicator for I/O-bound virtual-thread services. HTTP request rate more accurately reflects need to add pods.

**Trigger.** Pod CPU utilisation stays low (< 40 %) while p95 latency climbs and response-queue depth grows — classic symptom of CPU-metric-only HPA missing the actual load signal.

**Effort.** S — HPA YAML changes only; custom metrics adapter (Prometheus Adapter) already implied by existing Prometheus stack.

**Risk if skipped.** Virtual threads park on I/O, keeping CPU low even when service is saturated — the CPU HPA will never trigger.

**References.**
- Kubernetes HPA documentation: `https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/` (retrieved 2026-04-29) — multi-metric HPA and stabilisation window behaviour.

---

#### KEDA Event-Driven Autoscaling on Kafka Lag

**What.** Deploy KEDA and create `ScaledObject` resources for `search-service`, `notification-service`, and `order-service` Kafka consumers. Set `lagThreshold: 1000` per partition and `minReplicaCount: 1`, `maxReplicaCount: 20`.

**Why.** Standard HPA cannot observe Kafka consumer lag. KEDA bridges the 0-to-N scaling gap for event-driven consumers and can scale to zero during off-peak hours.

**Trigger.** Consumer lag > 10,000 messages for > 2 min on any topic, OR notification delivery SLO breach, OR search index lag > 5 min.

**Effort.** M — KEDA operator install + one `ScaledObject` YAML per consumer deployment.

**Applied 2026-04-30** — manifests at `k8s/base/scaling/` (notification-service, search-service, order-service) with per-environment maxReplicaCount overrides in `k8s/overlays/{staging,production}/scaling/`. Staging caps all three at 2; production runs notification-service 1->6, search-service and order-service 1->4. Requires KEDA operator installed via `kubectl apply -f https://github.com/kedacore/keda/releases/download/v2.14.0/keda-2.14.0.yaml`. Operator install + lag-test runbook lives at `monitoring/keda/README.md`.

**Risk if skipped.** Kafka consumers will fall behind under burst load and cannot self-heal without manual replica adjustment or CPU-triggered HPA (which won't fire for idle consumers).

**References.**
- KEDA Kafka scaler: `https://keda.sh/docs/2.14/concepts/scaling-deployments/` (retrieved 2026-04-29) — `ScaledObject` structure, polling interval, lag threshold configuration.

---

### 1.6 Bandwidth and CDN

#### CloudFront / Cloudflare for Images and Frontend Bundles

**What.** Serve all product images (from `media-service` storage) and Vite MFE bundles behind a CDN. Enable content-hashing on all JS/CSS assets (`[hash].js` filenames) and set `Cache-Control: public, max-age=31536000, immutable` for hashed assets. Use short TTL (60 s) for HTML entry points.

**Why.** Edge caching eliminates origin hits for static content, dramatically reducing bandwidth costs and improving Time to First Byte for global users. Immutable hashing means assets are cached permanently until the filename changes.

**Trigger.** `media-service` egress bandwidth > 1 TB/month, OR MFE bundle load time > 2 s on non-local connections, OR CDN not yet in place (applicable now).

**Effort.** M — CDN provisioning + Vite build config for content hashing.

**Risk if skipped.** Every product image request hits the origin; at catalogue scale (thousands of images, millions of daily page views) this is unsustainable.

**References.**
- CloudFront cache hit ratio documentation: `https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cache-hit-ratio-explained.html` (retrieved 2026-04-29).
- Vite production build guide covers `[hash]` filename patterns: `https://vitejs.dev/guide/build`.

---

## 2. Performance Optimizations

### 2.1 StructuredTaskScope for Fan-Out Queries

**What.** In `OrderCreationSaga` and any service method that calls 3+ independent downstream services sequentially, replace the sequential call pattern with `StructuredTaskScope.ShutdownOnFailure`. Each gRPC call becomes a forked subtask; all are cancelled automatically if one fails.

**Where to apply immediately:**
1. `OrderService.createOrder` — currently calls cart, inventory, payment, and promotion services. These are logically independent and can run in parallel.
2. Any aggregation endpoint that assembles data from multiple services (e.g., an order-detail view that fetches product info + user profile + tracking data).

**Why.** `StructuredTaskScope` is the structured-concurrency complement to virtual threads. Sequential I/O calls with virtual threads still pay full latency of each call; parallel fan-out pays only the max latency.

**Note.** `StructuredTaskScope` is a preview feature in Java 21 — requires `--enable-preview` at compile and runtime. Pin the flag in Maven Surefire and service `JAVA_TOOL_OPTIONS`.

**Trigger.** p95 order creation > 800 ms when all downstream services are healthy.

**Effort.** S (per method) — the API is straightforward; the main cost is identifying every sequential fan-out.

**Risk if skipped.** Virtual threads eliminate thread-pool exhaustion but do nothing for latency when calls are serialised.

**References.**
- Oracle Java 21 Structured Concurrency docs: `https://docs.oracle.com/en/java/javase/21/core/structured-concurrency.html` (retrieved 2026-04-29) — `ShutdownOnFailure`, `ShutdownOnSuccess`, fork/join pattern.

---

### 2.2 N+1 Query Audit

**What.** Enable Hibernate statistics (`spring.jpa.properties.hibernate.generate_statistics=true`) in staging and add a `@TransactionalEventListener` test that fails on any query count exceeding a configured threshold. Audit the following specific hot spots:

| Location | Risk | Fix |
|----------|------|-----|
| `ProductRepository.findByCategoryOrSubcategories` | 3-level parent chain traversal forces Hibernate to lazy-load `category.parent.parent` — potentially 3 extra SELECTs per product row | Rewrite with a single native query using recursive CTE or a flat `category_path` column |
| `ProductRepository.advancedSearch` (`:categoryId IS NULL OR p.category.parent.parent.id = :categoryId`) | Same 3-level traversal inside a paginated result set of potentially hundreds of rows | Same fix as above, or denormalise `category_path_ids` as an array column indexed with GIN |
| `OrderRepository.findByUserIdOrderByCreatedAtDesc` | `Order` → `OrderItems` likely lazy — iterating the result list for display triggers N item-list fetches | Add `@EntityGraph(attributePaths = {"items"})` or `JOIN FETCH` |
| `CartRepository` → `CartItem` → `Product` | Cart retrieval likely cascades N product lookups if items are lazy | `JOIN FETCH` in `CartRepository` or dedicated DTO projection query |

**Trigger.** Hibernate `statistics.queries.executed` > 5 × expected for a paginated list endpoint, OR datasource query count in integration test exceeds threshold, OR staging slow query log shows repeated single-row `SELECT`s with correlated IDs.

**Effort.** S–M per hot spot

**Risk if skipped.** An N+1 on a 20-item product list page generates 60+ queries (product + category + parent + grandparent). At 100 rps that is 6,000 DB round-trips per second from a single endpoint.

**References.**
- Hibernate statistics documentation: `https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#statistics`.

---

### 2.3 HikariCP Pool Sizing

**What.** Override HikariCP defaults (which set `maximumPoolSize=10`) using the formula: `pool_size = (number_of_vCPUs × 2) + effective_spindle_count`. For a 4-vCPU pod targeting a managed cloud database (SSD, no spindles): target = 9–12 connections per pod. Do **not** set large pools (e.g., 50) — this increases context switching and database pressure.

Set in `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
```

For virtual-thread services, also set `hikari.allow-pool-suspension=false` and monitor `hikari.pending-threads` via Prometheus — sustained > 0 pending threads signals pool exhaustion.

**Applied 2026-04-30** — values rolled out to user, product, cart, order, payment, inventory and promotion services: `maximum-pool-size: 20`, `minimum-idle: 5`, `connection-timeout: 3000`, `idle-timeout: 600000`, `max-lifetime: 1800000`, `leak-detection-threshold: 60000`, `pool-name: ${spring.application.name}-hikari`, `register-mbeans: true`, `validation-timeout: 1000`. The pool size was bumped from 10 to 20 because virtual threads multiply the number of concurrent in-flight DB calls (each virtual thread parks on connection acquisition rather than blocking a platform thread); a per-pod ceiling of 20 still fits comfortably under the per-DB `max_connections` (PostgreSQL 100, MySQL 151) across the projected pod count (≤4 pods/service in staging). `connection-timeout` was tightened to 3 s for fail-fast under saturation, `leak-detection-threshold` (60 s) gives observability into long-running transactions, and `register-mbeans: true` exposes the pool over JMX for ad-hoc debugging. Cart-service config lives in profile-specific YAMLs (`application-local.yml` and `application-docker.yml`) because its canonical `application.yml` is config-server style and intentionally bare; values were applied identically in both. Notification-service was skipped (MongoDB only). Search-service (Elasticsearch) and media-service (file storage / Mongo) also skipped — no JDBC datasource.

**Trigger.** `hikari.pending-threads` > 0 for more than 10 s, OR p99 latency spikes without corresponding CPU increase, OR DB max connections exceeded.

**Effort.** S

**Risk if skipped.** Undersized pools queue virtual threads waiting for a connection, negating the concurrency benefits of virtual threads. Oversized pools overload the database server.

**References.**
- HikariCP pool sizing wiki: `https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing` (retrieved 2026-04-29) — `connections = (core_count × 2) + effective_spindle_count`; smaller pools outperform larger ones on OLTP.

---

### 2.4 GC Tuning: G1GC or ZGC

**What.** Switch from the JVM default GC (ParallelGC in Java 21 for server-class machines) to G1GC or ZGC in services with large heap allocations or low-latency requirements.

- Use **G1GC** (default for JVM ≥ Java 9, but confirm with `-XX:+UseG1GC`) for most services. Set `-XX:MaxGCPauseMillis=200`.
- Use **ZGC** (`-XX:+UseZGC`) for `api-gateway` and `order-service` where p99 pause sensitivity is highest. ZGC targets < 1 ms GC pauses.

Explicitly set heap bounds: `-Xms256m -Xmx512m` for lightweight services, `-Xms512m -Xmx1g` for order/product/search services.

**Trigger.** GC pause time (measured via `jvm.gc.pause` Prometheus metric) > 200 ms p99, OR full GC events more than once per hour.

**Effort.** S — JVM flag changes in Kubernetes Deployment `JAVA_TOOL_OPTIONS`.

**Risk if skipped.** ParallelGC is throughput-optimised but causes stop-the-world pauses that can exceed 500 ms on large heaps, directly violating API latency SLOs.

**References.**
- OpenJDK ZGC documentation: `https://wiki.openjdk.org/display/zgc`.

---

### 2.5 Spring Boot AOT and GraalVM Native Image

**What.** Enable AOT processing for all services by adding `-Dspring.aot.enabled=true` to the build. Prioritise `api-gateway` for native image compilation using `mvn native:compile` with GraalVM — the gateway has no dynamic class loading and is the most startup-sensitive component. Target < 100 ms startup for the native gateway image.

**Caveats:** GraalVM native compilation does not support full dynamic reflection; verify all Jackson deserialisers, Hibernate entity scanning, and custom `BeanPostProcessor` implementations have AOT hints. Expect 10–30 min build times in CI.

**Trigger.** Pod startup time > 30 s (disrupts Kubernetes rolling deploys), OR cost optimisation requires scale-to-zero (native startup < 100 ms makes this viable).

**Effort.** M (gateway native image) — L (all services)

**Risk if skipped.** JVM cold-start times of 5–15 s per pod mean rolling deploys stall and scale-from-zero is impractical.

**References.**
- Spring Boot Native Image docs: `https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html` (retrieved 2026-04-29) — startup < 100 ms, RSS < 80 MB with native compilation.

---

### 2.6 Kafka Producer Batching and Compression

**What.** Configure all Kafka producers with explicit batching and compression settings:

```yaml
spring:
  kafka:
    producer:
      batch-size: 65536          # 64 KB (default 16 KB is too small for burst)
      properties:
        linger.ms: 5             # Wait up to 5 ms to fill a batch
        compression.type: zstd  # Best compression ratio; lz4 if CPU is constrained
        buffer.memory: 33554432  # 32 MB send buffer
```

`zstd` compression typically achieves 60–70 % size reduction on JSON event payloads at low CPU cost. `linger.ms=5` trades a small latency increase for batch efficiency.

**Trigger.** Kafka broker disk usage > 60 %, OR producer `record-send-rate` shows many single-record batches (avg batch size < 5 records), OR network egress costs from Kafka are notable.

**Effort.** S — producer config only.

**Risk if skipped.** Small batches and uncompressed payloads waste broker disk I/O and network bandwidth, reducing the effective throughput ceiling of the Kafka cluster.

**References.**
- Confluent Kafka producer tuning guide: `https://docs.confluent.io/cloud/current/client-apps/optimizing/throughput.html`.

---

### 2.7 Database Query Plan Caching and Index Audit

**What.**
1. Enable Hibernate's prepared statement reuse (enabled by default with `spring.jpa.open-in-view=false` + connection pool).
2. Audit `EXPLAIN (ANALYZE, BUFFERS)` output on the following queries that lack optimal index coverage:
   - `ProductRepository.searchByNameOrDescription` — the `LIKE '%term%'` pattern cannot use a B-tree index. Replace with a PostgreSQL `tsvector` full-text index or delegate to Elasticsearch.
   - `OrderRepository.findAbandonedOrders` — verify composite index on `(status, created_at)` exists.
   - `InventoryReservationRepository.findExpiredReservations` — needs partial index: `WHERE status = 'RESERVED'` on `expires_at`.
3. Add a composite index for the hot inventory query path: `CREATE INDEX CONCURRENTLY idx_reservation_status_expires ON inventory_reservations(status, expires_at) WHERE status = 'RESERVED'`.

**Trigger.** Any query appearing in `pg_stat_statements` with `mean_exec_time > 100 ms` and `calls > 1000`.

**Effort.** S (index creation) — M (LIKE → full-text migration)

**Risk if skipped.** The `LIKE '%term%'` search in `ProductRepository` is a sequential scan on every page load. At 10k products it is tolerable; at 100k it will time out.

**References.**
- PostgreSQL `pg_stat_statements` extension: `https://www.postgresql.org/docs/current/pgstatstatements.html`.

---

## 3. New Features

### 3.1 Personalised Recommendations

**Status:** Applied 2026-04-30 (Phase 1 — streaming co-occurrence). Implemented as a standalone `recommendation-service` (port 8092) consuming `order.created` from Kafka and exposing `GET /api/recommendations/product/{productId}` and `GET /api/recommendations/user/{userId}`. Algorithm and operational notes in [docs/RECOMMENDATIONS.md](RECOMMENDATIONS.md). Phase 2 (pgvector) remains open — trigger when unique-product count exceeds ~1M or when business asks for content-aware recs.

**What.** Phase 1 (batch, low effort): Consume `order.completed` and `product.viewed` Kafka events into a MongoDB collection. Run a nightly co-occurrence job that computes "users who bought X also bought Y". Expose as a `/recommendations/{productId}` endpoint on `product-service`.

Phase 2 (ML, high effort): Generate product and user embeddings using a lightweight model (e.g., matrix factorisation or sentence-transformer on description text). Store vectors in pgvector on PostgreSQL. Query with `SELECT id, name FROM products ORDER BY embedding <=> $1 LIMIT 10`.

**Expected impact.** Industry benchmarks show personalised recommendations drive 10–35 % of e-commerce revenue (Amazon attributes ~35 %; smaller platforms typically see 10–15 %).

**Effort.** S (batch co-occurrence) → L (pgvector embeddings + model pipeline)

**Risk if skipped.** Product discovery is limited to search and manual browsing; no cross-sell / upsell surface.

**References.**
- pgvector GitHub: `https://github.com/pgvector/pgvector` (retrieved 2026-04-29) — HNSW and IVFFlat indexes; cosine similarity (`<=>`) for normalised embeddings; ACID-compliant.

---

### 3.2 Real-Time Inventory Sync via SSE

**Status:** Applied 2026-04-30. Endpoint live at `/api/inventory/stream`; details in [docs/REALTIME_INVENTORY.md](REALTIME_INVENTORY.md). Migration trigger to WebSocket: >5k concurrent connections per pod.

**What.** Add a `GET /inventory/stream` endpoint in `inventory-service` (or expose via API gateway) that emits `SseEmitter` events whenever stock levels change for watched product IDs. The product detail MFE subscribes on mount and updates the "In Stock" indicator without polling.

Publish stock-change events to Kafka on every `InventoryRepository.save()`. An `@KafkaListener` in a thin SSE fan-out service pushes to all open emitters for the affected `productId`.

**Expected impact.** Eliminates the "add to cart → out of stock" failure path. Reduces cart abandonment due to stale availability data.

**Effort.** M

**Risk if skipped.** Without real-time sync, users add out-of-stock items to cart and hit errors at checkout — a high-friction experience that directly reduces conversion.

**References.**
- Spring MVC `SseEmitter` docs: `https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html` (retrieved 2026-04-29) — `SseEmitter`, `produces=text/event-stream`, heartbeat pattern.

---

### 3.3 Wishlist Service

**What.** Promote the existing stub in `user-dashboard-mfe` to a full backend microservice (`wishlist-service`). Store wishlists in MongoDB (`{ userId, items: [{productId, addedAt, priceAtAdd}] }`). Expose REST + Kafka events on `wishlist.item.added` for recommendation pipeline consumption.

**Expected impact.** Wishlists increase return visits and are a first-party signal for the recommendation engine. Typical e-commerce platforms see 5–15 % of users with active wishlists.

**Effort.** S — MongoDB schema is simple; the MFE stub already exists.

**Risk if skipped.** The MFE stub raises user expectations without delivering; also, price-drop alerts (a natural follow-on) are high-conversion features that depend on wishlist data.

**References.**
- MongoDB flexible schema is well-suited for user-generated collection data; no specific external reference needed beyond standard Spring Data MongoDB docs.

---

### 3.4 Reviews and Ratings

**What.** New `review-service` with PostgreSQL backend. Schema: `reviews(id, product_id, user_id, rating 1–5, title, body, created_at, verified_purchase)`. Expose GraphQL or REST. Aggregate `avg_rating` and `review_count` into the Elasticsearch product index as ranking signals via Kafka event projection.

**Expected impact.** Reviews increase conversion by 15–30 % on product pages (widely reported in e-commerce literature). Verified purchase flag builds trust.

**Effort.** M

**Risk if skipped.** Without reviews, the platform competes on price alone; no social proof layer.

**References.**
- Elasticsearch `function_score` query supports incorporating `avg_rating` as a ranking boost factor: `https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html`.

---

### 3.5 Multi-Currency and i18n

**What.** Add a `currency-service` (or a lightweight middleware in the API gateway) that fetches exchange rates from ECB/Open Exchange Rates API on a daily schedule and caches in Redis. Accept `Accept-Language` and `X-Currency` headers at the gateway. Store prices in a canonical currency (EUR or USD) and convert at response time.

For i18n: externalise all frontend strings to JSON locale files (`en.json`, `nl.json`) using `react-i18next`. Add a `translations` collection in MongoDB for admin-editable content strings.

**Expected impact.** Unlocks non-English markets; required for any expansion outside the domestic market.

**Effort.** M (currency conversion) + M (frontend i18n) = L total

**Risk if skipped.** Platform is limited to single-currency / single-language audience.

**References.**
- European Central Bank exchange rate API: `https://data.ecb.europa.eu/help/api/data`.
- `react-i18next`: `https://react.i18next.com/`.

---

### 3.6 Subscription and Recurring Orders

**What.** Extend `order-service` with a `Subscription` entity (`{ userId, items, interval: WEEKLY|MONTHLY, nextRunAt, status }`). A scheduled job (`@Scheduled`) fires daily, finds due subscriptions, and triggers `OrderCreationSaga` for each. Send `subscription.created` / `subscription.paused` Kafka events for notification service.

**Expected impact.** Subscriptions drive predictable recurring revenue and LTV. High value for consumables categories.

**Effort.** M (saga reuse) → add `@Scheduled` + subscription management endpoints.

**Risk if skipped.** No recurring revenue model; each purchase requires re-acquisition effort.

**References.**
- The `OrderCreationSaga` already exists — subscription orders reuse the same saga, reducing implementation risk significantly.

---

### 3.7 Loyalty and Tiered Pricing

**What.** Extend `promotion-service` to support loyalty tiers: `BRONZE / SILVER / GOLD` based on lifetime spend stored in the user profile. Apply tier-based discount rules in the existing promotion evaluation pipeline. Store tier history in the existing `promotions` table or a new `loyalty_tiers` table.

**Expected impact.** Loyalty programmes increase repeat purchase rates by 20–40 % (Bain & Company data).

**Effort.** S (if promotion service is cleanly abstracted, which it appears to be)

**Risk if skipped.** No incentive for repeat purchases beyond individual promotions.

---

### 3.8 Returns and RMA Flow

**What.** New `return-service` backed by an orchestration saga (`ReturnProcessingSaga`): validate return window → notify warehouse → trigger refund via payment-service → update inventory. Note: parallel refund saga development provides natural integration points.

**Expected impact.** Self-service returns reduce customer support tickets and improve NPS.

**Effort.** L (new service + saga)

**Risk if skipped.** Manual return handling at scale is operationally unsustainable.

---

### 3.9 Saved Payment Methods

**What.** Integrate Stripe Customer API and Payment Intent with `setup_future_usage: off_session`. Store Stripe `customer_id` and `payment_method_id` in `payment-service` (never raw card data). Present saved methods at checkout.

**Expected impact.** One-click checkout reduces cart abandonment. Industry studies show checkout friction is the #1 cause of cart abandonment.

**Effort.** M — Stripe SDK + minimal schema change.

**Risk if skipped.** Every purchase requires re-entering payment details; significant conversion loss on mobile.

**References.**
- Stripe Payment Intents with saved payment methods: `https://docs.stripe.com/payments/save-and-reuse`.

---

### 3.10 Cart Abandonment Recovery

**What.** The abandoned-cart cron (`findAbandonedOrders` in `OrderRepository`) already identifies stale orders. Add the email delivery path: publish a `cart.abandoned` Kafka event after 1 hour of inactivity; `notification-service` consumes it and sends a templated email (via existing MailHog / SendGrid path) with a recovery link and optional discount code.

**Expected impact.** Cart abandonment recovery emails achieve 5–15 % recovery rate industry-wide; low effort given existing infrastructure.

**Effort.** S — infrastructure exists; requires Kafka event + email template.

**Risk if skipped.** The detection logic already runs but produces no recovery action — wasted query cost with no revenue impact.

---

### 3.11 Search-as-You-Type with Debouncing and Highlight

**What.** Add an Elasticsearch `search_as_you_type` field type to the product mapping. Create a dedicated `/search/suggest` endpoint. In the React MFE, debounce the input with a 200 ms delay (`lodash.debounce` or `useDebounce` hook) before firing the suggest query. Return highlighted fragments (`highlight.fields.name`).

**Expected impact.** Autocomplete increases search engagement by 20–30 %; highlighted fragments confirm relevance at a glance.

**Effort.** S (ES mapping change) + S (MFE component)

**References.**
- Elasticsearch `search_as_you_type`: `https://www.elastic.co/guide/en/elasticsearch/reference/current/search-as-you-type.html`.

---

### 3.12 Faceted Search with ES Aggregations

**What.** Extend `search-service` to return aggregation buckets alongside hits: `terms` aggregation on `category.id`, `range` aggregation on `price`, `terms` on `brand`. The product-catalog MFE renders these as clickable filter chips, refining the query with `filter` clauses.

**Expected impact.** Faceted navigation is the primary product discovery mechanism on catalogue-heavy e-commerce platforms.

**Effort.** M — requires ES aggregation query design + MFE filter UI.

**References.**
- Elasticsearch aggregations: `https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html`.

---

## 4. Architectural Evolutions

### 4.1 Outbox Pattern via Debezium CDC

> **Status (2026-04-30).** A polling-based transactional outbox is now live in
> `order-service` and `payment-service`. See [`OUTBOX_PATTERN.md`](./OUTBOX_PATTERN.md)
> for the implementation guide. The Debezium CDC variant described below is
> the next-step migration target — defer until Postgres logical replication
> and Kafka Connect are available in the target environment, or sustained
> outbox throughput exceeds ~2 000 writes/s. The schema, headers, and
> consumer dedup contract are already in place, so swapping the polling
> relay for a Debezium connector is a configuration change, not a code
> change. Open follow-ups: extend the same pattern to `inventory-service`
> replenishment events; convert the cancel-flow choreography in
> `OrderCreationSaga` to also use the outbox publisher.

**What.** Replace the current dual-write pattern (write to DB then publish to Kafka in the same service) with a transactional outbox. Each service writes an `outbox_events` table row inside the same DB transaction as its domain write. Debezium CDC (Change Data Capture) reads the MySQL/PostgreSQL binlog and publishes the row to Kafka without any application-layer involvement.

**Why.** The current dual-write is not atomic: if the Kafka publish fails after the DB commit, the event is lost. If the DB write fails after Kafka publish, a ghost event propagates. The outbox pattern provides exactly-once DB + event publication using only DB transaction guarantees — no distributed transaction required.

**Where to apply first:**
1. `order-service` — `order.created` / `order.updated` events.
2. `payment-service` — `payment.completed` / `payment.failed` events (highest correctness criticality).

**Trigger.** Any incident involving inconsistent order/payment state traceable to a lost or duplicate Kafka event, OR code review identifying `save() + kafkaTemplate.send()` without compensating logic.

**Effort.** M — Debezium connector deployment (Docker/K8s) + `outbox_events` table migration + connector config per service.

**Risk if skipped.** Dual-write is a latent correctness bug that surfaces under network partitions, Kafka broker restarts, or application crashes between the DB commit and `kafkaTemplate.send()`.

**References.**
- Debezium outbox pattern blog post: `https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/` — covers the Debezium `outbox` event router SMT (Single Message Transform).
- Debezium architecture overview: `https://debezium.io/documentation/reference/stable/architecture.html`.

---

### 4.2 GraphQL Gateway / BFF

> **Status: APPLIED 2026-04-29 (feature/graphql-bff).** Embedded inside the
> existing `infrastructure/api-gateway` module. Schema, DataLoader rationale,
> auth surface and follow-ups documented in [`GRAPHQL_BFF.md`](./GRAPHQL_BFF.md).

**What.** Add a GraphQL layer on top of the Spring Cloud Gateway (or as a separate `bff-service`) using Spring for GraphQL. The React MFEs query for exactly the fields they need in a single round-trip, eliminating the current pattern where the frontend makes 3–5 REST calls to assemble a page.

**Example:** The order detail page currently requires: `GET /orders/{id}` + `GET /products/{id}` (for each item) + `GET /users/{id}` + `GET /inventory/{id}`. With GraphQL BFF, this collapses to one query with DataLoaders batching the N product lookups.

**Why.** N+1 REST round trips are the primary cause of MFE load latency. GraphQL `DataLoader` batching eliminates the per-item fetch pattern.

**Trigger.** MFE network waterfall shows > 4 sequential API calls on a single page, OR frontend team reports prop-drilling or over-fetching as a recurring pain point.

**Effort.** L — schema design, DataLoader implementation, security (field-level auth), and frontend migration.

**Risk if skipped.** As the feature surface grows (reviews, recommendations, loyalty tiers), the number of REST calls per page grows linearly, degrading TTI.

**References.**
- Spring for GraphQL reference: `https://docs.spring.io/spring-graphql/reference/index.html` (retrieved 2026-04-29) — annotated controllers, DataLoader integration, Apollo Federation support.

---

### 4.3 Event Sourcing for Orders and Inventory

**What.** Replace the `Order` and `InventoryReservation` mutable-state JPA model with an append-only event log (`OrderCreated`, `OrderConfirmed`, `OrderShipped`, `OrderCancelled`). Read models are projected from events via `@EventSourcingHandler`. Use Axon Framework (integrates with Spring Boot) or a custom Kafka-log-backed implementation.

**Why.** Orders require a full audit trail for legal and customer-service purposes. Event sourcing provides this for free. It also enables temporal queries ("what was the state at time T?") and replay for new read projections without ETL.

**Trigger.** Compliance requirement for order audit trail, OR need to rebuild read models from history, OR order reconciliation bugs traced to mutable-state updates.

**Effort.** L — significant rearchitecture of `order-service`; best introduced on a greenfield bounded context first.

**Risk if skipped.** Mutable-state models lose history; debugging payment or fulfilment discrepancies requires piecing together logs rather than replaying events.

**References.**
- Axon Framework Spring Boot starter: `https://docs.axoniq.io/axon-framework-reference/4.10/`.

---

### 4.4 API Versioning

**What.** Adopt a consistent `/api/v1/` prefix across all services (several currently use unversioned paths). Define a deprecation policy: v1 supported for 12 months after v2 GA. Add `Deprecation` and `Sunset` response headers to v1 endpoints once v2 is available (RFC 8594).

**Why.** The current mix of versioned and unversioned paths will create breaking-change risk as the MFEs evolve. Module Federation MFEs load independently — a backend breaking change silently breaks only the affected MFE.

**Trigger.** First time a REST API change would break an existing MFE contract (this is likely imminent as features are added).

**Effort.** S (new endpoints) → M (migrating existing)

**Risk if skipped.** Breaking changes to REST APIs will require coordinated multi-service deploys, eliminating independent deployability — the core MFE benefit.

**References.**
- RFC 8594 Sunset header: `https://datatracker.ietf.org/doc/html/rfc8594`.

---

### 4.5 Service Mesh (Istio)

**What.** Deploy Istio on the Kubernetes cluster. Enable auto mTLS between all pods via `PeerAuthentication: STRICT`. Use `VirtualService` and `DestinationRule` for canary deployments (traffic splitting by weight). Add Istio's built-in observability (Kiali, Jaeger integration).

**Why.** mTLS eliminates the possibility of inter-pod traffic interception within the cluster. Canary deployments allow 5 % → 20 % → 100 % traffic shifting for new MFE or service versions with automatic rollback on error rate SLO breach.

**Trigger.** Kubernetes cluster is production-grade (not just local Docker Compose), OR compliance requirement for encryption-in-transit between pods, OR need for fine-grained canary deploy control.

**Effort.** L — Istio is operationally complex; introduce after the K8s platform is stable.

**Risk if skipped.** Pod-to-pod traffic is unencrypted within the cluster (a network-layer attacker can intercept). Canary deployments require manual load-balancer weight changes.

**References.**
- Istio overview: `https://istio.io/latest/docs/concepts/what-is-istio/` (retrieved 2026-04-29) — mTLS, traffic shaping, canary deployments, observability.

---

### 4.6 Feature Flags (Unleash / OpenFeature)

**What.** Deploy Unleash (self-hosted, Apache 2.0 licence) or integrate OpenFeature SDK into the Spring Boot services and React MFEs. Wrap new MFE versions, experimental API endpoints, and risky backend changes behind feature flags. Use `userId` and `environment` as evaluation context.

**Why.** Feature flags decouple deployment from release, enabling dark launches, gradual rollouts, and instant kill-switches without redeployment. Particularly important for Module Federation MFEs, where a broken MFE version can be rolled back without a new deploy.

**Trigger.** First time a feature requires A/B testing, OR first time a rollback required a full redeployment (this indicates flags are overdue).

**Effort.** M — Unleash server deploy + SDK integration in each service/MFE.

**Risk if skipped.** All feature rollouts are binary (on/off at deploy time), making incident response slower and A/B testing impossible without infrastructure changes.

**References.**
- OpenFeature specification: `https://openfeature.dev/docs/reference/intro`.
- Unleash is production-used by Finn.no, GitLab, and others at scale.

---

### 4.7 Multi-Region Deployment

**What.** Deploy a second K8s cluster in a geographically distinct region. Use read-local strategy: reads served from the local region's replica, writes always routed to the primary region. Cart and session data use eventual consistency with vector-clock conflict resolution. DNS failover via Route 53 / Cloudflare with health checks.

**Why.** Single-region deployments have no tolerance for data centre outages. Multi-region also reduces latency for users far from the primary region.

**Trigger.** Availability SLO target > 99.9 % (single-region cannot reliably achieve 99.99 %), OR regulatory requirement to store data within a specific jurisdiction.

**Effort.** L — requires database replication topology changes, network peering, and conflict resolution strategy.

**Risk if skipped.** A regional cloud provider outage takes the entire platform offline.

**References.**
- AWS multi-region architecture guide: `https://aws.amazon.com/solutions/guidance/multi-region-application-architecture/`.

---

## 5. Operational Hardening

### 5.1 SLOs and Error Budgets

**What.** Define SLIs and SLOs for each of the 11 services. Record them in a `slo.yaml` per service. Example targets:

| Service | Availability SLO | Latency SLO (p95) | Window |
|---------|-----------------|-------------------|--------|
| api-gateway | 99.9 % | < 100 ms | 30 days |
| product-service | 99.5 % | < 200 ms | 30 days |
| order-service | 99.9 % | < 500 ms | 30 days |
| payment-service | 99.95 % | < 1000 ms | 30 days |
| search-service | 99.0 % | < 300 ms | 30 days |
| notification-service | 99.0 % | N/A (async) | 30 days |

Implement error budget burn rate alerts in Grafana (alert when budget is 50 % consumed in 1 hour).

**Trigger.** Before chaos engineering (§5.2) can be run, SLOs must exist to define "blast radius" acceptability. Treat absence of SLOs as the trigger.

**Effort.** S — the measurement infrastructure (Prometheus + Grafana) already exists; the missing piece is formal SLO documents and burn-rate alert rules.

**Risk if skipped.** No objective basis for making release/reliability trade-off decisions. Chaos experiments cannot be evaluated without a defined steady state.

**References.**
- Google SRE Workbook, *Implementing SLOs*: `https://sre.google/workbook/implementing-slos/` (retrieved 2026-04-29) — SLI format (good/total), error budget policy, quarterly SLO review cadence.

---

### 5.2 Chaos Engineering (Chaos Mesh)

**What.** Deploy Chaos Mesh on the K8s cluster. Define a starter experiment set:
1. `PodChaos` (pod-kill) on `inventory-service` — verify circuit breaker opens and order creation degrades gracefully.
2. `NetworkChaos` (network delay, 500 ms) between `order-service` and `payment-service` — verify timeout + retry behaviour.
3. `PodChaos` (pod-failure, 50 % of `notification-service` pods) — verify Kafka consumer lag stays bounded.

Run experiments quarterly with defined success criteria tied to SLOs (§5.1).

**Trigger.** SLOs defined (§5.1) + at least one full production deployment cycle completed without major incidents.

**Effort.** M — Chaos Mesh operator install + experiment YAML definitions.

**Risk if skipped.** Resilience4j circuit breakers and bulkheads are configured but never validated under realistic failure injection. Configuration errors are silent until a production incident.

**References.**
- Chaos Mesh PodChaos documentation: `https://chaos-mesh.org/docs/simulate-pod-chaos-on-kubernetes/` (retrieved 2026-04-29) — pod-kill, pod-failure, container-kill; label-based targeting.

---

### 5.3 Game Days and DR Drills

**What.** Schedule quarterly game days: define a failure scenario (e.g., "Kafka broker 1 of 3 fails"), assign roles (incident commander, comms, engineering), run the scenario in staging, measure MTTR, and retrospect. Separately, run a full DR drill annually: restore the production database from the latest backup, verify all services start against the restored state.

**Trigger.** Platform has been running in production for > 3 months with no structured failure drill. The longer this is deferred, the more untested the recovery paths become.

**Effort.** S (scheduling and process) — M (first run with setup)

**Risk if skipped.** Recovery procedures documented in `BACKUP_AND_DR.md` are theoretical until practiced. Actual MTTR in an incident will be much higher than estimated.

**References.**
- Netflix Chaos Monkey origins: `https://netflixtechblog.com/the-netflix-simian-army-16e57fbab116` — game days as organisational practice.

---

### 5.4 Secret Rotation Automation (External Secrets Operator)

**What.** Deploy External Secrets Operator (ESO) in K8s. Store all secrets (DB passwords, Auth0 client secrets, Stripe API keys, Kafka credentials) in HashiCorp Vault or AWS Secrets Manager. Create `ExternalSecret` CRDs that sync to Kubernetes `Secret` objects with a `refreshInterval: 1h`. Rotate secrets via the vault without touching K8s or application config.

**Why.** Manual secret rotation is a compliance gap and operationally error-prone. ESO automates propagation of rotated secrets to all pods.

**Trigger.** First compliance audit, OR first secret-exposure incident, OR platform moves to production with real customer data.

**Effort.** M — ESO operator install + Vault/Secrets Manager setup + migration of existing Kubernetes `Secret` objects.

**Risk if skipped.** Secrets embedded in `application.yml` or K8s `Secret` manifests (even base64-encoded) are frequently leaked in git history, CI logs, or misconfigured RBAC.

**References.**
- External Secrets Operator overview: `https://external-secrets.io/latest/introduction/overview/` (retrieved 2026-04-29) — `SecretStore` / `ExternalSecret` model, `refreshInterval` for automated rotation, 40+ providers.

---

### 5.5 Per-Tenant Resource Quotas

**What.** Once multi-tenancy lands (if it does), apply Kubernetes `ResourceQuota` and `LimitRange` per namespace per tenant. Set CPU and memory limits that prevent one tenant's workload from starving another. Use `NetworkPolicy` to enforce tenant isolation at the network layer.

**Why.** Without resource quotas, a noisy-neighbour tenant can exhaust cluster resources and degrade all other tenants.

**Trigger.** First multi-tenant deployment (this is a future-state item; log it as a hard requirement in the multi-tenancy design document).

**Effort.** S (once namespacing is in place)

**Risk if skipped.** A single tenant executing a bulk import or mis-configured scheduled job can cause cluster-wide OOM events.

**References.**
- Kubernetes ResourceQuota: `https://kubernetes.io/docs/concepts/policy/resource-quotas/`.

---

## Appendix: Confidence Levels

| Recommendation | Confidence | Basis |
|---------------|-----------|-------|
| Outbox / Debezium | High | Official Debezium docs + widely deployed pattern |
| StructuredTaskScope | High | Oracle Java 21 docs (preview API) |
| HikariCP pool formula | High | Official HikariCP wiki + Oracle benchmark data |
| KEDA Kafka scaling | High | Official KEDA docs |
| Elasticsearch shard sizing | High | Official ES docs |
| Redis Cluster trigger metrics | High | Official Redis docs |
| pgvector for recommendations | High | Official pgvector GitHub + HNSW/IVFFlat confirmed |
| Spring GraphQL BFF | High | Official Spring for GraphQL docs |
| GraalVM native gateway | High | Spring Boot Native Image docs |
| LTR (Learning to Rank) | High | Official ES LTR docs (ES 8.12+) |
| Kafka `batch.size` / `linger.ms` config values | Medium | Known Confluent recommendations; exact config page not accessible at time of research — treat specific values as starting points, verify against current Confluent/Apache Kafka docs |
| pgvector embedding pipeline details | Medium | GitHub README confirmed; specific batch size / model choice is context-dependent |
| Multi-region conflict resolution details | Low | General distributed-systems knowledge; no specific e-commerce reference grounded |
