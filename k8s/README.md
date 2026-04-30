# Kubernetes Manifests

Kustomize-based manifests for the e-commerce platform.

## Layout

```
k8s/
├── base/                      # Environment-agnostic resources
│   ├── namespace.yaml
│   ├── infra/                 # eureka, config-server, api-gateway
│   ├── backend/               # 10 microservices
│   ├── frontend/              # shell-app + 5 MFEs
│   ├── policies/              # NetworkPolicy (default deny + allows)
│   ├── ingress/               # NGINX ingress
│   ├── cronjobs/              # DB backups
│   ├── hpa.yaml               # HPAs for product/order/payment
│   └── kustomization.yaml
└── overlays/
    ├── staging/               # smaller replicas/resources, staging tags
    └── production/            # prod resources, immutable SemVer tags
```

## Architecture

```mermaid
flowchart TB
  Client((Client)) -->|HTTPS| Ingress[NGINX Ingress]
  Ingress -->|/| Shell[shell-app]
  Ingress -->|/api| Gateway[api-gateway]
  Gateway --> Eureka[(Eureka)]
  Gateway --> User[user-service]
  Gateway --> Product[product-service]
  Gateway --> Cart[cart-service]
  Gateway --> Order[order-service]
  Gateway --> Payment[payment-service]
  Gateway --> Inventory[inventory-service]
  Gateway --> Notification[notification-service]
  Gateway --> Search[search-service]
  Gateway --> Media[media-service]
  Gateway --> Promotion[promotion-service]
  User & Cart & Order & Payment & Inventory --> Postgres[(PostgreSQL)]
  Product & Promotion --> MySQL[(MySQL)]
  Notification & Media --> Mongo[(MongoDB)]
  Search --> ES[(Elasticsearch)]
  User & Product & Cart & Promotion --> Redis[(Redis)]
  Order & Payment & Inventory & Notification & Search --> Kafka[(Kafka)]
```

## Prerequisites

- A Kubernetes cluster (>= 1.28)
- `kubectl`, `kustomize` (built into kubectl since 1.14)
- An ingress controller (NGINX) and `cert-manager` if using Let's Encrypt
- A container registry containing `ecommerce/<service>:<tag>` images

## Apply

```bash
# Validate first (no cluster contact required)
kubectl apply --dry-run=client -k k8s/base
kubectl apply --dry-run=client -k k8s/overlays/staging
kubectl apply --dry-run=client -k k8s/overlays/production

# Real apply
kubectl apply -k k8s/overlays/staging
kubectl apply -k k8s/overlays/production
```

## Secrets

The base manifests reference two secrets that you MUST create out-of-band. Do NOT commit them.

```bash
# Application credentials
kubectl create secret generic ecommerce-secrets \
  -n ecommerce-prod \
  --from-literal=POSTGRES_USER='...' \
  --from-literal=POSTGRES_PASSWORD='...' \
  --from-literal=MYSQL_USER='...' \
  --from-literal=MYSQL_PASSWORD='...' \
  --from-literal=MONGO_INITDB_ROOT_USERNAME='...' \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD='...' \
  --from-literal=REDIS_PASSWORD='...' \
  --from-literal=ELASTIC_PASSWORD='...' \
  --from-literal=AUTH0_ISSUER_URI='...' \
  --from-literal=AUTH0_AUDIENCE='...' \
  --from-literal=AUTH0_DOMAIN='...' \
  --from-literal=STRIPE_API_KEY='...'

# Backup credentials (S3)
kubectl create secret generic backup-secrets \
  -n ecommerce-prod \
  --from-literal=AWS_ACCESS_KEY_ID='...' \
  --from-literal=AWS_SECRET_ACCESS_KEY='...' \
  --from-literal=S3_BUCKET='ecommerce-backups-prod'

# Config server git creds
kubectl create secret generic config-server-secrets \
  -n ecommerce-prod \
  --from-literal=CONFIG_GIT_URI='https://...' \
  --from-literal=CONFIG_GIT_USERNAME='...' \
  --from-literal=CONFIG_GIT_PASSWORD='...'
```

### Recommended: Sealed Secrets

For GitOps workflows, use [bitnami-labs/sealed-secrets](https://github.com/bitnami-labs/sealed-secrets):

```bash
kubectl create secret generic ecommerce-secrets --dry-run=client -o yaml \
  --from-literal=POSTGRES_PASSWORD='...' \
  | kubeseal --format=yaml > k8s/overlays/production/sealed-secrets/ecommerce-secrets.yaml
```

The resulting `SealedSecret` is safe to commit; only the cluster's controller can decrypt it.

Alternatives: HashiCorp Vault + the External Secrets Operator, or AWS Secrets Manager via ESO.

## Open Items For Production Go-Live

- Replace `example.com` in `base/ingress/ingress.yaml` with the real domain.
- Provision a TLS certificate (`cert-manager` ClusterIssuer) and confirm the secret name `ecommerce-tls`.
- Push images to a private registry; update overlay `images:` blocks.
- Choose a secret backend (Sealed Secrets / Vault / cloud secret manager).
- Wire data tier (PostgreSQL/MySQL/MongoDB/Kafka/Elasticsearch) — these manifests assume those run as in-cluster StatefulSets or as managed services. A managed-DB approach is recommended for prod.
- Configure pod disruption budgets per StatefulSet for data-tier components.
