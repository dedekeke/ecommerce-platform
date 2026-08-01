# Recommendations (Phase 1: Streaming Co-Occurrence)

## Why this design

We start with the simplest thing that ships revenue: a **streaming co-occurrence matrix in MongoDB**. No ML library, no embeddings, no nightly batch job — every `order.created` event nudges two counters per pair, and recommendations fall out as a top-N indexed read. Phase 2 (pgvector / matrix factorisation) is sketched in [SCALING_AND_IMPROVEMENTS.md §3.1](SCALING_AND_IMPROVEMENTS.md) and is justified only when product-catalog cardinality or signal sources outgrow this approach.

This was deliberate:

- **Time-to-first-impact**: a junior dev can debug a co-occurrence count. Nobody can debug a 768-dim vector at 02:00.
- **Hot-path simplicity**: the read path is one indexed Mongo query. Cache for 60s in front, done.
- **Honest economics**: even "naive" co-occurrence captures the highest-leverage signal — what people actually bought together. Industry benchmarks attribute 10-35% of e-commerce revenue to recommendations; most of that lift is from the simple cases.

## Algorithm

For an order containing **N distinct** products, we generate `N * (N - 1)` directed pair upserts. Each upsert is on the composite key `"<productId>:<otherProductId>"` with `$inc: {count: 1}` and `$setOnInsert` for the productId fields. Storing **both directions** (a→b and b→a) keeps the read path a single indexed query: "for productId X, give me the top-N rows".

Per-user recommendations aggregate co-occurrence counts across every product the user owns, exclude already-owned products, and sort. This is a small in-memory reduce — fine while user purchase histories stay below a few hundred items.

## Idempotency

Kafka is at-least-once. We protect the matrix with an orderId-keyed `consumed_orders` collection: the first ingest writes the doc; the second hits a duplicate-key error and the consumer drops the event. This is the same pattern used by `notification-service`'s replenishment saga — keep it consistent across the platform.

## Indexing strategy

| Collection | Index | Purpose |
|---|---|---|
| `co_occurrence` | `_id` (composite "a:b") | Atomic upserts; deterministic naming |
| `co_occurrence` | `{productId: 1, count: -1}` (compound) | Top-N per-product read in a single index seek |
| `user_purchases` | `_id` = userId | Direct fetch in personalised path |
| `consumed_orders` | `_id` = orderId | O(1) idempotency lookup |

The `{productId: 1, count: -1}` compound is the only non-trivial one: leading `productId` lets the planner narrow to one product's row set; trailing `-1` on `count` makes the sort an index walk instead of an in-memory sort. Verify with `db.co_occurrence.find({productId: "X"}).sort({count: -1}).limit(10).explain("executionStats")` — should report `IXSCAN` and `nReturned == limit`.

## Caching

`@Cacheable` with a Caffeine cache, TTL 60s, max 10k entries per cache (`productRecommendations`, `userRecommendations`). If a request burst is the issue, raise `maximumSize` first; if cross-pod consistency starts to matter, swap the cache manager to Redis without changing the service code.

## API examples

```bash
# Anonymous: products commonly bought with p-123, top 10
curl http://localhost:8080/api/recommendations/product/p-123?limit=10

# Personalised: requires Auth0 JWT
curl -H "Authorization: Bearer $JWT" \
     http://localhost:8080/api/recommendations/user/auth0|abc123?limit=20
```

Response shape (both endpoints):

```json
[
  { "productId": "p-456", "score": 17 },
  { "productId": "p-789", "score": 12 }
]
```

`score` is the raw or aggregated co-occurrence count today; treating it as opaque ordinal score keeps room for recency-decay or conversion-rate weighting later without an API break.

## Scaling concerns / migration triggers

Co-occurrence storage grows quadratically with catalog size in the worst case (`O(P^2)` documents). In practice it grows with the number of distinct co-purchased pairs, which for typical e-commerce is sub-quadratic but still meaningful. Watch for:

| Signal | Trigger | Migration |
|---|---|---|
| `co_occurrence` collection > 50 GB | Sharding or vector store | pgvector + product embeddings |
| Per-product top-N latency > 50ms p99 even with index | Working set exceeds RAM | Materialise top-N per product into a separate `recommendations_top` collection populated by a daily job |
| > 1M unique products | Algorithmic limits of pair counting | Vespa / OpenSearch with vector retrieval, or pgvector HNSW |
| Need cold-start recs for brand new SKUs | No co-occurrence signal exists | Content-based fallback using product description embeddings |

Until any of those fire, this design is the right one.

## Backfill from existing orders

The current ingest picks up new orders only. To bootstrap from history, run a one-shot **Kafka replay** with a fresh `group-id` so the consumer starts from `auto-offset-reset: earliest` against the existing `order.created` topic. If the topic retention has already truncated old orders, an alternative is a small batch script that paginates through `order-service`'s database, synthesises `order.created` payloads, and publishes them to a dedicated `recommendation.backfill` topic that this service also listens on (idempotency guarantees no double-counting). `mongorestore` from a precomputed dump is the third option for environments where running the replay is gated.
