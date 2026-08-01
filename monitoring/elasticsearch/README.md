# Elasticsearch Operations

> Back to [README](../../README.md).

## ILM Policy: `products-policy`

Hot-warm-cold-delete lifecycle for the `products` index, applied at search-service boot via `ElasticsearchIndexInitializer.ensureIlmPolicy`. PUT `_ilm/policy` is upsert; safe to run on every startup.

| Phase | Min age | Actions |
|-------|---------|---------|
| Hot | 0 | rollover at 7d or 50 GB |
| Warm | 7d | force-merge to 1 segment, shrink to 1 shard |
| Cold | 30d | freeze (read-only, low-priority) |
| Delete | 90d | delete |

Policy JSON: [`ilm/products-policy.json`](ilm/products-policy.json) (the search-service ships a copy at `services/search-service/src/main/resources/elasticsearch/ilm/products-policy.json` so it boots without a network mount).

## Verification

```bash
# Confirm policy registered
curl -s http://localhost:9200/_ilm/policy/products-policy | jq

# Inspect index lifecycle status
curl -s http://localhost:9200/products/_ilm/explain | jq

# Force a phase transition (testing only)
curl -X POST http://localhost:9200/_ilm/move/products -H 'Content-Type: application/json' \
  -d '{"current_step":{"phase":"hot","action":"complete","name":"complete"},"next_step":{"phase":"warm"}}'
```

## When tier transitions kick in

ILM evaluates on a 10-minute poll by default. Set `indices.lifecycle.poll_interval` lower in dev to see transitions quickly.

## Migrating existing indices

The policy applies to NEW indices via the bound index template only. To migrate an existing `products` index:

```bash
curl -X PUT http://localhost:9200/products/_settings -H 'Content-Type: application/json' \
  -d '{"index.lifecycle.name":"products-policy","index.lifecycle.rollover_alias":"products"}'
```

## When to revisit
- Total index size > 200 GB → tighten hot rollover from 50 GB to 25 GB.
- Cold-tier disk pressure → push delete from 90d to 60d.
- Frequent force-merge OOM in warm → bump warm phase node memory or drop `forcemerge`.
