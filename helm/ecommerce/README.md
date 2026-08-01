# ecommerce (umbrella chart)

Helm umbrella chart for the e-commerce platform. Bundles 11 backend services as
file-system subcharts in `./charts`.

## Layout

```
helm/ecommerce/
├── Chart.yaml              # umbrella, declares 11 dependencies
├── values.yaml             # cross-cutting defaults (global registry, ingress, per-service)
├── values-staging.yaml
├── values-prod.yaml
├── templates/
│   ├── _helpers.tpl
│   └── ingress.yaml        # /api -> api-gateway, / -> shell-app
└── charts/
    ├── api-gateway/        # generic backend chart (Deployment+Service+ConfigMap+HPA+PDB)
    ├── user-service/
    ├── product-service/
    ├── cart-service/
    ├── order-service/
    ├── payment-service/
    ├── inventory-service/
    ├── notification-service/
    ├── search-service/
    ├── media-service/
    └── promotion-service/
```

## Install

```bash
# Lint everything
helm lint ./helm/ecommerce

# Install/upgrade staging
helm upgrade --install ecommerce ./helm/ecommerce \
  -f helm/ecommerce/values-staging.yaml \
  -n ecommerce-staging --create-namespace

# Install/upgrade production
helm upgrade --install ecommerce ./helm/ecommerce \
  -f helm/ecommerce/values-prod.yaml \
  -n ecommerce-prod --create-namespace
```

## Secrets

Create `ecommerce-secrets` and `backup-secrets` out-of-band before installing —
see `k8s/README.md` for the full `kubectl create secret` commands. The chart
references the secrets via `envFrom.secretRef.optional: true`, so install will
not fail if the secret is absent yet, but pods will crash-loop until it exists.

## Registry & domain (environment-driven)

Image refs render as `<global.imageRegistry>/<service>:<global.imageTag>` via the shared `svc.image`
helper. Registry defaults to `ghcr.io/ecommerce-platform`; the ingress host defaults to the
`${BASE_DOMAIN}` placeholder. Override per environment — never commit a real registry/domain:

```bash
helm upgrade --install ecommerce ./helm/ecommerce \
  --set global.imageRegistry="$IMAGE_REGISTRY" \
  --set global.imageTag=1.2.3 \
  --set global.ingress.host="$BASE_DOMAIN" \
  -f helm/ecommerce/values-prod.yaml
```

TLS: the ingress is annotated `cert-manager.io/cluster-issuer` (from
`global.ingress.certManager.clusterIssuer`); the issuer + `ecommerce-tls` Certificate live in
`k8s/cert-manager/`. Vault HA values + per-service ESO policies live in `helm/vault/`.

## Open items

- Add Frontend MFE subcharts (currently k8s/base/frontend/* covers them; replicate as helm subcharts when frontends graduate to helm).
- Wire data-tier dependencies (Postgres/MySQL/Mongo/Redis/Kafka) — currently assumed pre-provisioned.
- Wire `helm test` hooks for smoke tests.
