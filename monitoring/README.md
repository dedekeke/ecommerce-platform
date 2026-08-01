# Centralized Logging — Loki + Promtail + Grafana

Why Loki over ELK: lower resource cost, label-indexed (logs themselves are
unindexed), native Grafana integration, simpler ops for a small platform team.

## Components

| Component | Purpose                                              |
|-----------|------------------------------------------------------|
| Loki      | Log storage and query (LogQL)                        |
| Promtail  | Log shipper — DaemonSet on k8s, container on Docker  |
| Grafana   | UI + dashboards + alerting                           |

## Local stack (Docker Compose)

A simple `docker-compose.monitoring.yml` already exists at the repo root for
Prometheus + Grafana. Add Loki + Promtail to it (or use a separate file) by
referencing the values in `monitoring/loki/` and `monitoring/promtail/`.

## Kubernetes (recommended)

```bash
# 1. Create namespace
kubectl create namespace monitoring

# 2. Add Grafana Helm repo
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# 3. Install Loki (single-binary mode + S3 storage; tweak the bucket names first)
helm upgrade --install loki grafana/loki \
  -f monitoring/loki/loki-values.yaml \
  -n monitoring

# 4. Install Promtail (DaemonSet)
helm upgrade --install promtail grafana/promtail \
  -f monitoring/promtail/promtail-values.yaml \
  -n monitoring

# 5. Install Grafana with provisioned datasources + dashboards
helm upgrade --install grafana grafana/grafana \
  --set adminPassword='change-me' \
  --set-file 'datasources.datasources\.yaml=monitoring/grafana/provisioning/datasources/loki.yaml' \
  --set 'dashboardProviders.dashboardproviders\.yaml.providers[0].name=ecommerce' \
  --set 'dashboardProviders.dashboardproviders\.yaml.providers[0].folder=ecommerce' \
  --set 'dashboardProviders.dashboardproviders\.yaml.providers[0].type=file' \
  --set 'dashboardProviders.dashboardproviders\.yaml.providers[0].options.path=/var/lib/grafana/dashboards/ecommerce' \
  -n monitoring

# 6. Import the bundled dashboard
kubectl -n monitoring create configmap grafana-dashboard-services-logs \
  --from-file=monitoring/grafana/dashboards/services-logs.json \
  --dry-run=client -o yaml | kubectl apply -f -
```

## Dashboards shipped

- `services-logs.json` — per-service log volume, error rate, live error feed,
  rolling error count.

Plug additional dashboards into `monitoring/grafana/dashboards/`. Anything in
that folder will be auto-loaded by the provisioning sidecar.

## Open items

- Switch S3 bucket names (`ecommerce-loki*`) to real ones.
- Wire Grafana alerts (email / Slack / PagerDuty) for sustained error rate.
- Add structured-logging config to every Spring Boot service so `level`,
  `service`, and `traceId` come through as JSON fields (Logback config).
