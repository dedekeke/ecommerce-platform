# Resource Quotas + LimitRanges

> Back to [README](../../README.md). Scaling-doc reference: §5.5.

Per-namespace resource ceilings prevent any single bounded context (or tenant, when multi-tenancy lands) from starving its neighbours. Defined as `ResourceQuota` + `LimitRange`.

## Quotas

| Namespace | CPU req | CPU lim | Mem req | Mem lim | Pods |
|-----------|---------|---------|---------|---------|------|
| `ecommerce` (prod) | 20 | 40 | 40Gi | 80Gi | 200 |
| `ecommerce-staging` | 10 | 20 | 20Gi | 40Gi | 100 |
| `chaos-testing` | 2 | 4 | 4Gi | 8Gi | 50 |

Quota mode is **Soft** (only requests are quota-enforced). Limits prevent a single pod from grabbing the whole node but don't count toward namespace quota — this is the recommended mode per [Kubernetes docs](https://kubernetes.io/docs/concepts/policy/resource-quotas/).

## LimitRanges

Default per-container request: `100m` CPU, `128Mi` memory. Default limit: `500m` CPU, `512Mi` memory. Min: `50m` / `64Mi`. Max: `2` / `4Gi`.

These give pods sane defaults if their Deployment forgets to set resources, and reject pods that try to grab more than the max.

## Apply

```bash
kubectl apply -k k8s/quotas
```

Verify:
```bash
kubectl describe quota -n ecommerce
kubectl describe limitrange -n ecommerce
```

## When to revisit

- Pods evicted under `quotaExceeded` → bump the appropriate ceiling.
- Multi-tenant launch → split `ecommerce` into `tenant-<id>` namespaces, each with a smaller per-tenant quota; add a NetworkPolicy preventing cross-namespace traffic.
- HPA can scale beyond quota — coordinate `maxReplicas` with quota CPU ceiling, otherwise scaling stalls silently.
