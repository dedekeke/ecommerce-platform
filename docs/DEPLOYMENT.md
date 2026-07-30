# Deployment Guide

Step-by-step deployment of the e-commerce platform across the three supported targets:

1. [Local development](#3-local-full-stack-bring-up) — `docker-compose.yml` (+ optional `docker-compose.monitoring.yml`)
2. [Staging (Kubernetes)](#4-staging-deployment) — `cd-staging.yml` → Helm umbrella chart
3. [Production (Kubernetes)](#5-production-deployment) — git tag `v*.*.*` → `cd-production.yml` → Helm

Related runbooks (cross-linked, not duplicated here):

| Topic | Document |
|---|---|
| Incident triage, rollback details, DR drills | [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md) |
| Backup schedules, restore procedures, RTO/RPO | [BACKUP_AND_DR.md](BACKUP_AND_DR.md) |
| Trusted-proxy deploy checklist, auth model | [SECURITY.md](SECURITY.md#trusted-proxy-configuration-deploy-checklist) |
| DB `max_connections` sizing vs replica counts | [db-connection-budget.md](db-connection-budget.md) |
| Kubernetes manifests reference | [../k8s/README.md](../k8s/README.md) |
| Helm umbrella chart reference | [../helm/ecommerce/README.md](../helm/ecommerce/README.md) |

> **Honesty callout — what is verified vs aspirational.**
> The local compose path is exercised daily. The Kubernetes path is **CI-lint-verified but has never been applied to a real cluster**: no cluster credentials exist beyond the `KUBECONFIG_STAGING` / `KUBECONFIG_PROD` GitHub secrets (unpopulated placeholders), `REGISTRY: ghcr.io` carries a literal `# placeholder` comment in both CD workflows, and **no `v*.*.*` release tag has ever been cut**, so `cd-production.yml` has never run. Sections describing those flows document what the code *does when triggered*, plus the known gaps you must close first (see [§5.4](#54-known-gaps-before-first-production-deploy)).

---

## 1. Prerequisites

### 1.1 Local development

| Requirement | Version | Check |
|---|---|---|
| Java JDK | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 22.12.0 (pinned in `.nvmrc` — run `nvm install && nvm use`) | `node --version` |
| Docker + Compose | latest | `docker --version` |
| Git | latest | `git --version` |

`scripts/setup-local-dev.sh` additionally shells out to host CLIs: `psql`, `pg_isready`, `mysqladmin`, `redis-cli`, `mongosh`, `curl`.

### 1.2 Kubernetes (staging / production)

From [`k8s/README.md`](../k8s/README.md): Kubernetes >= 1.28, `kubectl` + `kustomize`, `helm`, NGINX ingress controller, cert-manager, and a container registry holding `ecommerce/<service>:<tag>` images. Cluster add-ons used by the manifests (install per their READMEs before first deploy):

- **cert-manager** — `k8s/cert-manager/README.md` (Let's Encrypt `ClusterIssuer`s `letsencrypt-prod` / `letsencrypt-staging`, HTTP-01 on ingress class `nginx`)
- **External Secrets Operator + Vault** — `k8s/secrets/README.md` and `helm/vault/README.md` (Vault KV v2, k8s auth, per-service `eso-<svc>` roles bootstrapped by `helm/vault/bootstrap.sh`)
- **KEDA v2.14** — `monitoring/keda/README.md` (Kafka-lag autoscaling for order/search/notification and friends)
- **Strimzi** — only if applying `k8s/dr/kafka-mirrormaker2.yaml`
- **Chaos Mesh** — optional, staging only (`k8s/chaos/`)

### 1.3 CI/CD (GitHub Actions)

Repository secrets consumed by the CD workflows (names only): `KUBECONFIG_STAGING`, `KUBECONFIG_PROD`, `BASE_DOMAIN`, plus the auto-provided `GITHUB_TOKEN` for ghcr.io pushes. Optional: `NVD_API_KEY` (dependency-check in `quality.yml`), `PERF_AUTH_TOKEN` / `vars.STAGING_GATEWAY_URL` (perf smoke). The `production` GitHub environment must be configured with required reviewers — that is the manual gate ([§5.2](#52-the-manual-gate)).

---

## 2. Environment configuration (the `.env` contract)

**Never commit real values.** `.gitignore` already excludes `.env`, `.env.local`, `.env.*.local`, `production.env`, `*.production.env`. This section lists variable **names** only.

### 2.1 Which template to copy

Two templates exist at the repo root and they are **not** interchangeable:

- **`.env.template`** — infra-shaped: DB credentials, Kafka, Elasticsearch, Eureka/Config Server, gateway tuning, CORS, `VITE_*` frontend vars, feature flags. This is what `QUICKSTART.md` and `README.md` tell you to copy, and it is the closer starting point for `docker compose up`.
- **`.env.example`** — app-secrets-shaped: Auth0, Stripe, Twilio/FCM, mail, tracing, deployment/DR. Its DB-credential block is commented out. `scripts/setup-local-dev.sh` and `scripts/build-all.sh --deploy` reference *this* file.

```bash
cp .env.template .env
```

> **Known gap:** neither template ships uncommented values for `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — all five are interpolated by `docker-compose.yml`. Add them to your `.env` by hand or the MySQL container will refuse to initialise and five Postgres-backed services will get empty datasource credentials. Also note compose reads `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` (not the `MONGODB_USER` / `MONGODB_PASSWORD` names QUICKSTART mentions).

Minimum local `.env` (names — set your own local-only values):

```
POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB / POSTGRES_MAX_CONNECTIONS
MYSQL_ROOT_PASSWORD / MYSQL_USER / MYSQL_PASSWORD / MYSQL_MAX_CONNECTIONS
MONGO_INITDB_ROOT_USERNAME / MONGO_INITDB_ROOT_PASSWORD
SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
AUTH0_DOMAIN / AUTH0_ISSUER_URI / AUTH0_CLIENT_ID / AUTH0_CLIENT_SECRET / AUTH0_AUDIENCE
GATEWAY_TRUSTED_PROXIES=            # deliberately EMPTY locally — gateway is directly exposed
```

Auth0 values can stay placeholders for backend-only work: every service in `docker-compose.yml` runs with `SECURITY_ENABLED: "false"` (hardcoded in the compose file — setting it in `.env` does not affect containers). For real logins, follow [AUTH0_SETUP.md](AUTH0_SETUP.md).

### 2.2 Provider switches with safe local defaults

| Switch | Local default | Real credentials needed only when |
|---|---|---|
| `PAYMENT_PROVIDER` | `mock` | `stripe` → `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_API_BASE` |
| `SMS_PROVIDER` | `noop` | `twilio` → `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` |
| `PUSH_PROVIDER` | `noop` | `fcm` → `FCM_CREDENTIALS_PATH`, `FCM_PROJECT_ID` |
| Mail | MailHog (`MAIL_HOST=localhost`, `MAIL_PORT=1025`; use `MAIL_HOST=mailhog` from inside Docker) | production SMTP → `MAIL_HOST/PORT/USERNAME/PASSWORD`, `EMAIL_FROM` |
| `CURRENCY_RATE_API_URL` | empty (Flyway-seeded rates) | live FX rates |

### 2.3 Production env contract (`production.env.example`)

`docker-compose.prod.yml` loads secrets from `production.env` (gitignored). Copy `production.env.example` → `production.env` and fill every value; in real production source these from a secret manager (Vault / AWS Secrets / SOPS), as the file header says. Groups (names only):

- **Image**: `IMAGE_TAG` — must be an immutable SemVer; `:latest` is forbidden by the tagging policy in the compose header.
- **Datastores**: `POSTGRES_USER/PASSWORD/DB`, `MYSQL_ROOT_PASSWORD/USER/PASSWORD`, `MONGO_INITDB_ROOT_USERNAME/PASSWORD`, `REDIS_PASSWORD`, `ELASTIC_PASSWORD`, `SPRING_DATASOURCE_USERNAME/PASSWORD`.
- **Connection ceilings**: `POSTGRES_MAX_CONNECTIONS`, `MYSQL_MAX_CONNECTIONS` — **required, no `:-` defaults**; an unset value renders `max_connections=` and the DB container fails to start. Size per [db-connection-budget.md](db-connection-budget.md).
- **Auth0**: `AUTH0_DOMAIN`, `AUTH0_ISSUER_URI`, `AUTH0_AUDIENCE`, `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`.
- **Mail / payments / storage**: `MAIL_*`, `STRIPE_API_KEY`, `S3_BUCKET`, `S3_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
- **Config server git backend**: `CONFIG_GIT_URI`, `CONFIG_GIT_USERNAME`, `CONFIG_GIT_PASSWORD`.
- **Tracing**: `TRACING_SAMPLING_PROBABILITY_PROD` (keep well below 1.0 — a single Zipkin collector backs it).

> **Known gap:** `production.env.example` omits `GATEWAY_TRUSTED_PROXIES` even though `docker-compose.prod.yml` reads it and defaults it to **empty**. In compose-prod the gateway sits behind a TLS-terminating LB (`443:8080`), so empty is the *wrong* value there — rate limits on guest endpoints become spoofable. Add it explicitly. See [§5.3](#53-prod-critical-environment-values) and [SECURITY.md](SECURITY.md#trusted-proxy-configuration-deploy-checklist).

---

## 3. Local full-stack bring-up

### 3.1 Build before compose

Three services (`product-service`, `cart-service`, `order-service`) build their images from `Dockerfile.runtime`, which wraps a **host-built JAR**. Build first, always:

```bash
mvn clean install -DskipTests        # or: ./scripts/build-all.sh
```

> `QUICKSTART.md` orders these steps the other way around (compose up, then build). For those three services that order fails on a clean checkout.

### 3.2 Start the stack

`docker-compose.yml` has **no profiles** — a bare `up -d` builds and starts all 22 services (10 infra containers + eureka/config/gateway + 9 microservices):

```bash
docker compose up -d --build
docker compose ps      # wait until healthchecks report healthy
```

To start only the infrastructure tier (for running services via Maven instead), name the services, as the scripts do:

```bash
docker compose up -d postgres mysql mongodb redis zookeeper kafka elasticsearch zipkin mailhog
```

Dependency ordering is encoded in compose `depends_on: service_healthy` conditions — Kafka waits for Zookeeper; config-server and every microservice wait for eureka-server; gateway waits for eureka + redis; each service waits for its datastore. Slow starters have extended `start_period`s (Kafka 40s, Elasticsearch/gateway/microservices 60s) — give the stack ~2 minutes on first boot.

Key host ports (dev compose):

| Tier | Service : port |
|---|---|
| Datastores | postgres 5432, mysql 3306, mongodb 27017, redis 6379, elasticsearch 9200, kafka 9092 (host) / `kafka:29092` (in-network) |
| Platform | eureka 8761, config-server 8888, **api-gateway 8080** |
| Services | user 8081, product 8082, cart 8083, order 8084 (gRPC 9094), payment 8085 (gRPC 9090), inventory 8086 (gRPC 9091), search 8088, media 8089, promotion 8090 |
| Tooling | zipkin 9411, mailhog UI 8025 (SMTP 1025), redis-exporter 9121 |

> **Not in dev compose:** `notification-service` (8087), `review-service`, `recommendation-service`. Run them via Maven if needed: `./scripts/run-service.sh notification-service`.
>
> **Port drift warning:** when run via Maven (`local` profile) instead of Docker, search/media/promotion bind **8089/8090/8091** (their `application.yml` defaults) rather than the 8088/8089/8090 compose mappings. `scripts/check-services.sh` checks the Maven-local ports, so it reports those three DOWN against a Docker-run stack. The README/QUICKSTART port tables reflect the Docker mapping.

### 3.3 Alternative: services via Maven (hybrid mode)

```bash
./scripts/setup-local-dev.sh      # infra containers + health probes
./scripts/run-all-services.sh     # eureka → config → services → gateway, logs under logs/
./scripts/check-services.sh       # curl-based health sweep (exits 1 if anything is down)
./scripts/stop-all-services.sh    # kills Maven PIDs only — docker compose down separately
```

`run-all-services.sh` starts services in this order with 5s gaps: eureka-server, config-server, user, product, cart, order, payment, inventory, notification, search, media, promotion, api-gateway, then product-catalog-mfe + shell-app. It uses Spring profiles `local,personal` — copy the per-service `application-personal.yml.example` files first (see [LOCAL_DEV_PROFILE.md](LOCAL_DEV_PROFILE.md)).

> Do not run `setup-local-dev.sh` (which containerises eureka) and then `run-all-services.sh` (which starts eureka via Maven) without stopping the container first — both bind 8761.

### 3.4 Frontend dev servers

The canonical entry point is:

```bash
./scripts/run-frontend.sh
```

It builds+serves all five MFEs, then starts the shell — Angular MFEs must be up **before** the shell so native-federation import maps register:

| App | Port | Mode |
|---|---|---|
| product-catalog-mfe | 5001 | vite build + preview |
| cart-mfe | 5002 | vite build + preview |
| checkout-mfe | 5003 | vite build + preview |
| user-dashboard-mfe (Angular) | 5004 | ng build + `npx serve` |
| admin-dashboard-mfe (Angular) | 5005 | ng build + `npx serve` |
| shell-app (host) | 5173 | `npm run dev` |

Ports are load-bearing: the shell hardcodes remote entries at `http://localhost:5001..5005` in `frontend/shell-app/vite.config.ts`. (`run-all-services.sh` only starts the shell + product-catalog, so cart/checkout/dashboard remotes 404 in that path — use `run-frontend.sh` for a full UI.)

### 3.5 Verify

```bash
curl http://localhost:8080/actuator/health   # gateway
open http://localhost:8761                   # Eureka — all instances registered
open http://localhost:8080/swagger-ui.html   # aggregated API docs
```

### 3.6 Local monitoring stack (optional)

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

Brings up **Prometheus (9090), Grafana (3000, admin/admin), Zipkin (9411), Kafka UI (8080)** on the existing `ecommerce-network`. Notes:

- Run it **alongside** the main stack (the network is `external: true`; kafka-ui `depends_on: kafka`).
- **Port clash:** kafka-ui maps host 8080, colliding with api-gateway. Stop one or remap.
- The main dev compose already runs its own zipkin on 9411 — starting both files duplicates it.
- Prometheus mounts `config/prometheus/prometheus.yml` + `config/prometheus/alerts.yml` only. Scrape targets are **static** (`<service>:<port>/actuator/prometheus`); review/recommendation services are not scraped. The SLO burn-rate rules in `monitoring/prometheus/alerts/` are **not mounted** here — they target the Kubernetes path. `prometheus.yml` also points alerting at `alertmanager:9093`, which exists in **no** compose file (no Alertmanager anywhere yet).
- Grafana auto-provisions the dashboards in `config/grafana/dashboards/`: services-overview, service-health, jvm-metrics, business-metrics, circuit-breaker-monitoring, redis-monitoring, batch-jobs-monitoring.

Centralized logging (Loki + Promtail) is **Kubernetes-only** today — see `monitoring/README.md` for the Helm installs; adding Loki to the local compose file is a documented open item there.

### 3.7 Production-shaped compose (single node)

`docker-compose.prod.yml` is a hardened single-node variant: pre-built `ecommerce/<service>:${IMAGE_TAG}` images (no builds, no source mounts), `SPRING_PROFILES_ACTIVE=prod`, `SECURITY_ENABLED=true` everywhere, `restart: always`, per-container memory/CPU limits, json-file log rotation, and **only** the gateway published (`443:8080`, TLS assumed terminated at an upstream LB). It includes `notification-service` and `shell-app` (absent from dev compose) but no MFEs, review or recommendation services.

```bash
cp production.env.example production.env   # fill values; never commit
docker compose -f docker-compose.prod.yml --env-file production.env up -d
```

> No CI pipeline publishes the un-prefixed `ecommerce/<service>` image names this file expects — CD publishes to `ghcr.io/<owner>/ecommerce/<service>`. Treat compose-prod as a pattern for a self-managed node: build/tag images yourself (or retag the ghcr ones) before use.

---

## 4. Staging deployment

### 4.1 CI-driven path (`.github/workflows/cd-staging.yml`)

Staging deploys are **manually dispatched** — nothing auto-deploys on push to `develop`:

```bash
gh workflow run cd-staging.yml -f ref=develop     # or any branch/tag/sha
```

What the workflow does:

1. **Build & push backend** — 15-service matrix (eureka-server, config-server, api-gateway + 12 services incl. review & recommendation), Java 21, `mvn package -DskipTests -pl <path> -am`, image per service.
2. **Build & push frontend** — 6-app matrix (shell + 5 MFEs), Node 22.12.0.
3. Tags pushed to ghcr.io: `ghcr.io/<owner>/ecommerce/<name>:staging-<sha>` and `:staging-latest`.
4. **Deploy** (GitHub environment `staging`): `helm lint` with `values-staging.yaml` → kustomize render of `k8s/overlays/staging` (validation only — output discarded) → kubeconfig from `KUBECONFIG_STAGING` → 
   ```bash
   helm upgrade --install ecommerce ./helm/ecommerce \
     -f helm/ecommerce/values-staging.yaml \
     --set global.imageRegistry=ghcr.io/<owner>/ecommerce \
     --set global.imageTag=staging-<sha> \
     --set global.ingress.host=staging.<BASE_DOMAIN> \
     -n ecommerce-staging --create-namespace --wait --timeout 10m
   ```
5. **Smoke test** — `kubectl -n ecommerce-staging rollout status deploy/api-gateway --timeout=5m` (that is the entire automated check; do the [§6 checklist](#6-post-deploy-smoke-test-checklist) manually).

**Helm is the deploy mechanism.** Kustomize is only rendered for validation in CI. Do not `kubectl apply -k` into a namespace Helm manages — you would get duplicate Deployments/Ingress with conflicting ownership.

### 4.2 Manual path

Pick **one** mechanism per namespace and stay with it.

**Helm (matches CI):**

```bash
helm lint ./helm/ecommerce -f helm/ecommerce/values-staging.yaml
helm upgrade --install ecommerce ./helm/ecommerce \
  -f helm/ecommerce/values-staging.yaml \
  --set global.imageRegistry="$IMAGE_REGISTRY" \
  --set global.imageTag=staging-latest \
  --set global.ingress.host="staging.$BASE_DOMAIN" \
  -n ecommerce-staging --create-namespace --wait --timeout 10m
```

`global.imageRegistry` and `global.ingress.host` are `required`-guarded — the chart refuses to render without them. Chart scope caveats (from `helm/ecommerce/README.md` open items): the umbrella chart covers **11 backend services only** — no eureka/config-server, no frontends, no review/recommendation, and the data tier (Postgres/MySQL/Mongo/Redis/Kafka) is *assumed pre-provisioned*. The ingress routes `/` → `shell-app:80`, which the chart itself does not deploy — frontends currently live in `k8s/base/frontend/` only.

**Kustomize (alternative, do not mix with Helm):**

```bash
kubectl apply --dry-run=client -k k8s/overlays/staging   # validate
kubectl apply -k k8s/overlays/staging                    # namespace ecommerce-staging, 1 replica, small resources
```

The overlay `kustomization.yaml` contains `${IMAGE_REGISTRY}` placeholders — `envsubst` it first (CI does exactly that into a temp copy).

**Secrets** must exist before pods stop crash-looping (the chart mounts `ecommerce-secrets` with `optional: true`, so install succeeds but pods wait). Either create them manually per `k8s/README.md` (`ecommerce-secrets`, `backup-secrets`, `config-server-secrets`) or run the ESO+Vault flow (`k8s/secrets/README.md`, `helm/vault/README.md`), then `kubectl apply -k k8s/secrets`.

> **Known gap:** everything under `k8s/secrets/`, `k8s/cert-manager/certificate.yaml` and `k8s/quotas/ecommerce-quota.yaml` hardcodes `namespace: ecommerce`, while workloads deploy to `ecommerce-staging` / `ecommerce-prod`. Until that is reconciled, ESO-produced secrets and the TLS certificate materialise in a namespace no pod runs in — re-namespace them when applying. Also, ESO writes per-service `<svc>-secret` objects while the workloads consume the single `ecommerce-secrets`; the two models are not yet wired together.

---

## 5. Production deployment

### 5.1 Release flow (tag → `cd-production.yml`)

> **Never executed to date.** The repo has zero tags and zero releases, so this workflow has never fired. The steps below describe exactly what the YAML does when the first tag lands — expect first-run issues (see §5.4).

1. Cut a release from `master` (develop → master via PR first, per branch policy):
   ```bash
   git checkout master && git pull
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The tag push triggers **CD - Production**: same 15-backend + 6-frontend build matrices, images tagged `ghcr.io/<owner>/ecommerce/<name>:1.0.0` (`VERSION = tag minus v`) and `:sha-<git-sha>` (kept for debug/rollback pinning).
3. The `deploy` job pauses on the **manual gate** (below), then:
   ```bash
   helm lint + kustomize render (validation)
   helm upgrade --install ecommerce ./helm/ecommerce \
     -f helm/ecommerce/values-prod.yaml \
     --set global.imageRegistry=ghcr.io/<owner>/ecommerce \
     --set global.imageTag=<VERSION> \
     --set global.ingress.host=<BASE_DOMAIN> \
     -n ecommerce-prod --create-namespace --wait --timeout 15m
   kubectl -n ecommerce-prod rollout status deploy/api-gateway --timeout=10m
   ```

`values-prod.yaml` gives every service 2 replicas, larger resources for gateway/user/product/order/payment, and HPAs (2→8) on api-gateway/product/order/payment. Concurrency group `cd-production` prevents overlapping deploys.

### 5.2 The manual gate

The deploy job declares `environment: production`. The gate is **GitHub environment protection**, not YAML: in *Settings → Environments → production*, configure required reviewers (and optionally a wait timer). Without that configuration the "gate" approves nothing — verify it exists before the first tag.

### 5.3 Prod-critical environment values

- **`GATEWAY_TRUSTED_PROXIES`** — required per-environment deploy setting, not a tuning knob. Governs which peers' `X-Forwarded-For` the gateway trusts; guest-checkout/cart rate limiting and the `/api/admin/**` IP whitelist depend on it. Kubernetes/Helm ship the RFC1918 triple `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16` — safe-by-topology **only if your CNI enforces NetworkPolicy** (Calico/Cilium/GKE DPv2 do; plain Flannel does not); narrow it to the actual ingress CIDR. Full checklist + verification commands: [SECURITY.md](SECURITY.md#trusted-proxy-configuration-deploy-checklist). Verify after deploy:
  ```bash
  kubectl -n ecommerce-prod exec deploy/api-gateway -- printenv GATEWAY_TRUSTED_PROXIES
  kubectl -n ecommerce-prod logs deploy/api-gateway | grep "ClientIpResolver initialised"
  ```
- **`SECURITY_ENABLED=true`** everywhere (hardcoded in prod compose; confirm in k8s ConfigMaps) with real `AUTH0_ISSUER_URI` / `AUTH0_AUDIENCE`.
- **`POSTGRES_MAX_CONNECTIONS` / `MYSQL_MAX_CONNECTIONS`** — must satisfy the [connection budget](db-connection-budget.md) at max replica counts (KEDA can scale order/payment to 8).
- **Image tags** — immutable SemVer only, never `:latest` (policy in the `docker-compose.prod.yml` header).
- **`TRACING_SAMPLING_PROBABILITY_PROD`** — keep ≈0.1; full-rate tracing overwhelms the single Zipkin collector.
- **Secrets** via Vault + ESO ([k8s/secrets/README.md](../k8s/secrets/README.md)); remember the documented convention that `remoteRef.key` is the logical path (`services/payment-service`), never `kv/data/...`.

### 5.4 Known gaps before first production deploy

Tracked here so the guide doesn't pretend they're solved:

1. **No cluster** — `KUBECONFIG_PROD`, `BASE_DOMAIN` secrets are unpopulated; no cloud provider/cluster is referenced anywhere in the repo.
2. **Registry placeholder** — `REGISTRY: ghcr.io` is commented `# placeholder` in both CD workflows.
3. **Namespace mismatch** — ESO secrets / Certificate / quota hardcode `namespace: ecommerce` vs workloads in `ecommerce-prod` (§4.2 callout).
4. **Secret-model split** — ESO's `<svc>-secret` objects vs the `ecommerce-secrets` the charts actually consume.
5. **Helm chart coverage** — no eureka/config-server/frontend/review/recommendation subcharts; ingress `/` route targets a `shell-app` Service the chart doesn't create; data tier assumed pre-provisioned.
6. **No rollback automation** — no `--atomic`, no failure handler in CD; rollback is manual (§7).
7. **Kafka bootstrap drift** — compose uses `kafka:29092`, Helm configmaps use `kafka:9092`; whichever Kafka you provision must match.
8. **recommendation-service** has a KEDA ScaledObject but no Deployment manifest; **review-service** and the frontends are missing from the overlay `images:` rewrite lists.
9. **No Alertmanager** deployed anywhere, so SLO alerts have nowhere to route (§8).

---

## 6. Post-deploy smoke-test checklist

CI's only automated check is `kubectl rollout status deploy/api-gateway`. Run this manually after every staging/production deploy (substitute the namespace):

```bash
NS=ecommerce-staging   # or ecommerce-prod
kubectl -n $NS get pods                       # all Running/Ready, no CrashLoopBackOff
kubectl -n $NS get deploy                     # READY equals desired for all services
kubectl -n $NS get ingress ecommerce-ingress  # ADDRESS assigned
```

- [ ] `curl -fsS https://<host>/api/actuator/health` → `{"status":"UP"}` (gateway, via ingress)
- [ ] `curl -fsS https://<host>/api/products?page=0&size=1` → 200 with payload (read path: gateway → product-service → MySQL)
- [ ] Guest cart: `POST /api/cart/guest` succeeds (Redis + per-IP rate-limit path; hammering it should eventually return 429)
- [ ] Auth round-trip: login via Auth0, call an authenticated endpoint → 200; call without token → 401 (proves `SECURITY_ENABLED=true`)
- [ ] Order placement in staging (golden path): create order → payment (mock/stripe test) → order status progresses (Kafka + saga path). See `performance-tests/k6/scenarios/golden-path.js`, runnable via the `perf-smoke.yml` workflow with `base_url=https://<host>`.
- [ ] Email: notification-service dispatches (MailHog in dev; real SMTP in prod)
- [ ] `kubectl -n $NS logs deploy/api-gateway --since=10m | grep -iE "error|exception"` — nothing recurring
- [ ] Trusted-proxy verification commands from [§5.3](#53-prod-critical-environment-values)
- [ ] TLS: certificate is the cert-manager `ecommerce-tls` secret, not the ingress default; `kubectl -n $NS describe certificate ecommerce-tls` shows `Ready=True`

If any check fails and can't be fixed forward quickly → [§7 Rollback](#7-rollback).

---

## 7. Rollback

There is **no automated rollback** in either CD workflow (no `--atomic`, no failure handler). A failed `helm upgrade --wait` leaves the release in `failed` state; recover manually. Authoritative procedures live in [OPERATIONS_RUNBOOK.md §4](OPERATIONS_RUNBOOK.md#4-rollback-procedures); summary:

**Helm rollback (preferred — whole release):**

```bash
helm history ecommerce -n ecommerce-prod
helm rollback ecommerce <REVISION> -n ecommerce-prod --wait --timeout 5m
kubectl get pods -n ecommerce-prod -w
```

**Single service — image tag pinning:** every release publishes both `:<version>` and `:sha-<git-sha>` tags, so any previous build is pinnable without a rebuild:

```bash
kubectl set image deployment/<svc> <svc>=ghcr.io/<owner>/ecommerce/<svc>:1.4.6 -n ecommerce-prod
kubectl annotate deployment/<svc> kubernetes.io/change-cause="Rollback to 1.4.6 — <incident>" -n ecommerce-prod --overwrite
kubectl rollout status deployment/<svc> -n ecommerce-prod
```

or `kubectl rollout undo deployment/<svc> -n ecommerce-prod --to-revision=<N>`.

**Database migrations do not roll back** — Flyway is forward-only. If the bad release included a migration, follow [OPERATIONS_RUNBOOK.md §5](OPERATIONS_RUNBOOK.md#5-database-migration-rollback) (repair / forward-fix / PITR via [BACKUP_AND_DR.md](BACKUP_AND_DR.md)). Roll back code only if the schema change was backward-compatible.

**Compose-prod rollback:** set `IMAGE_TAG` in `production.env` back to the previous immutable version and `docker compose -f docker-compose.prod.yml --env-file production.env up -d`.

After any rollback, rerun the [§6 checklist](#6-post-deploy-smoke-test-checklist) and file the incident per [OPERATIONS_RUNBOOK.md §10](OPERATIONS_RUNBOOK.md#10-after-the-incident).

---

## 8. Monitoring verification

After a deploy (and after bringing up monitoring locally):

**Prometheus targets** — `http://localhost:9090/targets` (local) — every job UP:
- `infrastructure` tier: prometheus, eureka-server:8761, config-server:8888, api-gateway:8080, redis-exporter:9121
- `business` tier: user 8081, product 8082, cart 8083, order 8084, payment 8085, inventory 8086, notification 8087, search 8088, media 8089, promotion 8090 — all scraping `/actuator/prometheus`
- Known blind spots: review-service and recommendation-service have **no scrape jobs**; discovery is static (no k8s SD yet), so the target list must be maintained by hand.

**Grafana dashboards** — `http://localhost:3000` (admin/admin — change immediately outside a laptop), folder *E-Commerce*: services-overview, service-health, jvm-metrics, business-metrics, circuit-breaker-monitoring, redis-monitoring, batch-jobs-monitoring. Kubernetes path adds *SLOs / Error Budgets* and *services-logs* (Loki) — provisioning commands in `monitoring/README.md`.

**SLO alerts** — `monitoring/prometheus/alerts/slo-burn-rate.yml` defines 22 multiwindow burn-rate alerts (`<Service>FastBurn` paging at 14.4× budget over 1h, `<Service>SlowBurn` ticketing at 6× over 6h) against the availability SLOs in [`monitoring/slos/slos.md`](../monitoring/slos/slos.md) (99.9% gateway/order/cart/inventory/promotion/user, 99.95% product/payment, 99.5% search, 99.0% notification/media). Verify they are loaded: Prometheus → *Status → Rules* → group `slo-burn-rate` present, 22 rules, state `OK`/`inactive`.

> **Known gaps:** these rule files are only wired for the Kubernetes Prometheus — `docker-compose.monitoring.yml` mounts `config/prometheus/alerts.yml` only. And **no Alertmanager is deployed anywhere** although `prometheus.yml` targets `alertmanager:9093`, so today alerts evaluate but page no one; severity routing is an acknowledged open item in `slos.md`. Alert annotations link to `docs/OPERATIONS_RUNBOOK.md#<service>` anchors that don't exist yet in that file — navigate to [§2 Common Alerts → Triage](OPERATIONS_RUNBOOK.md#2-common-alerts--triage) instead.

**Tracing** — Zipkin `http://localhost:9411` (or the in-cluster `zipkin.ecommerce.svc:9411`): search for a recent gateway trace and confirm spans cross service boundaries. Prod sampling is `TRACING_SAMPLING_PROBABILITY_PROD` (~10%), so absence of a specific trace is expected — presence of *some* traces is the check.

**Logs (Kubernetes)** — Grafana → Explore → Loki: `{namespace="ecommerce-prod"}` returns fresh lines; *services-logs* dashboard shows per-service volume with no error-rate spike post-deploy.
