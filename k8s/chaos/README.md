# Chaos Engineering — Chaos Mesh

> Back to [README](../../README.md). Scaling-doc reference: §5.2.

We use [Chaos Mesh](https://chaos-mesh.org) for fault injection. Manifests in this directory are starter experiments — verify each in staging before promoting to production.

## Install

```bash
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm install chaos-mesh chaos-mesh/chaos-mesh \
  -n chaos-testing --create-namespace \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock
```

Verify operator pod is up:
```bash
kubectl -n chaos-testing get pods
```

## Experiments

| File | Type | Target | Expected blast radius |
|------|------|--------|----------------------|
| `pod-kill-product-service.yaml` | PodChaos | one product-service pod, every 10 min | HPA replaces; SLO unaffected |
| `network-delay-payment.yaml` | NetworkChaos | 200 ms latency on payment-service ↔ Postgres | order-service circuit breaker trips, fallback path runs |
| `cpu-stress-search.yaml` | StressChaos | 80 % CPU on search-service for 5 min | KEDA scales out; latency stays within p95 SLO |
| `network-partition-kafka.yaml` | NetworkChaos | partition notification-service from Kafka for 2 min | outbox-relayed events queue, lag clears within 5 min |

Each manifest carries `annotations.chaos-mesh.org/cron` so you can pin scheduled runs and `spec.duration` so they self-terminate.

## Game-day workflow

1. Open the runbook for the experiment.
2. Watch Grafana SLO dashboard + per-service log volume.
3. Apply the experiment: `kubectl apply -f <file>`.
4. Observe expected behaviour vs actual.
5. Stop early if SLO error budget burns >5 % in 1 h: `kubectl delete -f <file>`.
6. Capture findings in `docs/dr-runs/YYYY-MM-DD-<name>.md`.

## Production safeguards

- All experiments target `ecommerce-staging` namespace by default. **Do not run untested experiments in prod.**
- ChaosMesh's `dryRun` field is set on every manifest — flip to `false` only after staging signoff.
- Workflow CRD pins blast-radius: `spec.selector.namespaces` MUST be set; bare `mode: all` is forbidden.

## When to revisit

- Add `dns-chaos.yaml` once we depend on coreDNS for cross-region DNS.
- Add `time-chaos.yaml` once we have token-expiry edge cases that bite under clock skew.
