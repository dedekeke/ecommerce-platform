# Developer Onboarding Guide

> Audience: new engineers joining the platform team. Time budget: 1–2 hours from clone to first successful golden-path order.
> Back to [README](../README.md).

---

## 1. Prerequisites

| Tool      | Version       | Install                                                              |
|-----------|---------------|----------------------------------------------------------------------|
| JDK       | Java 21       | `sdk install java 21-tem` (sdkman) or Homebrew `brew install openjdk@21` |
| Maven     | 3.9+          | `brew install maven`                                                 |
| Node.js   | 22.12+        | `nvm install && nvm use` (root has `.nvmrc`)                         |
| Docker    | Latest        | Docker Desktop                                                       |
| kubectl   | 1.28+         | `brew install kubectl` (only needed for k8s/helm work)               |
| helm      | 3.14+         | `brew install helm`                                                  |
| Git       | 2.30+         | `brew install git`                                                   |

Verify with: `java -version && mvn -version && node -v && docker info && kubectl version --client && helm version`.

---

## 2. Clone & Configure

```bash
git clone https://github.com/your-org/ecommerce-platform.git
cd ecommerce-platform

cp .env.template .env                    # docker-compose secrets
# Open .env and set POSTGRES_*, MYSQL_*, MONGODB_*, AUTH0_*, REDIS_*
```

<details>
<summary>Per-service personal profile (recommended)</summary>

Each backend service ships an `application-personal.yml.example`. Copy and fill them in once:

```bash
for f in services/*/src/main/resources/application-personal.yml.example; do
  cp "$f" "${f%.example}"
done
cp services/media-service/src/main/resources/application-personal.properties.example \
   services/media-service/src/main/resources/application-personal.properties
```

`scripts/run-all-services.sh` activates the `personal` profile alongside `local`. Full details in [LOCAL_DEV_PROFILE.md](LOCAL_DEV_PROFILE.md).
</details>

---

## 3. Start Infrastructure

```bash
docker-compose up -d
docker-compose ps    # wait until all are healthy (~30s)
```

Brings up: PostgreSQL, MySQL, MongoDB, Redis, Kafka+Zookeeper, Elasticsearch, Zipkin, Prometheus, Grafana, MailHog.

---

## 4. Backend Startup Order

The order matters because services register with Eureka and pull configuration from Config Server.

```bash
mvn clean install -DskipTests       # ~3 min first time

# Sequential (recommended for first run):
./scripts/run-service.sh eureka-server      # 8761 — wait until UP
./scripts/run-service.sh config-server      # 8888 — optional
./scripts/run-service.sh api-gateway        # 8080
./scripts/run-service.sh user-service       # 8081
# ... repeat for product, cart, order, payment, inventory, notification, search, media, promotion

# Or all at once:
./scripts/run-all-services.sh
./scripts/check-services.sh
```

Logs are in `logs/<service-name>.log`.

---

## 5. Frontend Startup

```bash
nvm use
./scripts/run-frontend.sh        # builds + previews all React MFEs
```

Then visit Shell App at `http://localhost:5173`. Module Federation pulls the MFEs at runtime.

---

## 6. Golden Path Verification

1. Browse to `http://localhost:5173/products` — product grid loads (8+ items).
2. Click a product → Add to cart → Open cart → Checkout.
3. Complete the checkout form with a test card (`4242 4242 4242 4242`).
4. Open MailHog at `http://localhost:8025` — confirm an order-confirmation email arrived.
5. Order appears under `/dashboard/orders` (User Dashboard MFE).

If any step fails, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## 7. Running Tests

```bash
mvn test                              # backend unit tests (all modules)
mvn verify                            # + integration tests (Testcontainers)
mvn jacoco:report                     # coverage → target/site/jacoco/

# Frontend (run in each MFE folder OR loop through):
for d in frontend/*-mfe frontend/shell-app; do (cd "$d" && npm test -- --run); done

# E2E (Playwright)
cd e2e && npm install && npx playwright test
```

Coverage targets and current numbers: [COVERAGE_REPORT.md](COVERAGE_REPORT.md).

---

## 8. Tooling Tour

<details>
<summary>Where to find things</summary>

| Topic                       | Doc                                                          |
|-----------------------------|--------------------------------------------------------------|
| Architecture overview       | [ARCHITECTURE.md](ARCHITECTURE.md)                           |
| Quick start                 | [../QUICKSTART.md](../QUICKSTART.md)                         |
| Local dev profile           | [LOCAL_DEV_PROFILE.md](LOCAL_DEV_PROFILE.md)                 |
| Auth0 setup                 | [AUTH0_SETUP.md](AUTH0_SETUP.md)                             |
| API reference               | [API_DOCUMENTATION.md](API_DOCUMENTATION.md) + Swagger at `/swagger-ui.html` |
| Caching                     | [CACHING_STRATEGY.md](CACHING_STRATEGY.md)                   |
| Resilience patterns         | [RESILIENCE_PATTERNS.md](RESILIENCE_PATTERNS.md)             |
| Tracing                     | [TRACING_SETUP.md](TRACING_SETUP.md)                         |
| Email notifications         | [EMAIL_NOTIFICATIONS.md](EMAIL_NOTIFICATIONS.md)             |
| Scheduled tasks             | [SCHEDULED_TASKS.md](SCHEDULED_TASKS.md)                     |
| Security                    | [SECURITY.md](SECURITY.md), [SECURITY_SCAN.md](SECURITY_SCAN.md) |
| Performance testing         | [PERFORMANCE_TESTING.md](PERFORMANCE_TESTING.md)             |
| MFE integration testing     | [MFE_INTEGRATION_TEST.md](MFE_INTEGRATION_TEST.md)           |
| Frontend design system      | [frontend-design-brief.md](frontend-design-brief.md)         |
| IntelliJ run configurations | [IDEA_RUN_CONFIGURATIONS.md](IDEA_RUN_CONFIGURATIONS.md)     |
| Troubleshooting             | [TROUBLESHOOTING.md](TROUBLESHOOTING.md)                     |
| Backup & DR                 | [BACKUP_AND_DR.md](BACKUP_AND_DR.md)                         |
| Production runbook          | [OPERATIONS_RUNBOOK.md](OPERATIONS_RUNBOOK.md)               |

</details>

---

## 9. Common First-Day Issues

- Port already in use → `lsof -ti:<port> | xargs kill -9`
- Service not in Eureka → check `logs/<svc>.log` and `EUREKA_URI` in `.env`
- Auth0 401 locally → set `SECURITY_ENABLED=false` in `.env`
- Frontend can't reach gateway → CORS already includes `http://localhost:5173`

Questions → `#platform-eng` on Slack. Welcome aboard.
