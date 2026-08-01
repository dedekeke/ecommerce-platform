# Secret Management — External Secrets Operator + Vault

> Back to [README](../../README.md). Scaling-doc reference: §5.4.

## Why

Sealed Secrets keep cipher-text in git but rotate manually. External Secrets Operator (ESO) pulls live values from a backend (Vault, AWS Secrets Manager, GCP Secret Manager) so:

- No secret cipher-text in git.
- Rotation in Vault propagates without redeploying.
- Audit log of secret reads.

## Install

```bash
# ESO
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace \
  --set installCRDs=true

# Vault (dev mode for local; use Cloud Vault or HA cluster in prod)
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault \
  -n vault --create-namespace \
  --set "server.dev.enabled=true"
```

## Wiring

`vault-cluster-secret-store.yaml` registers a `ClusterSecretStore` that points at the in-cluster Vault. `*-externalsecret.yaml` files declare an `ExternalSecret` per service that materialises a Kubernetes `Secret` named `<svc>-secret` from a Vault path like `services/<svc>`.

### `remoteRef.key` convention (KV v2 — IMPORTANT)

The SecretStore/ClusterSecretStore declares the KV engine **mount** and version:

```yaml
provider:
  vault:
    path: "kv"        # the KV v2 engine mount point
    version: "v2"
```

Because the store already knows the mount is `kv` and the engine is KV **v2**, the
ESO Vault provider transparently inserts the `data/` segment KV v2 requires. Therefore
`remoteRef.key` MUST be the **logical path only** — no `kv/` mount prefix and no
`data/` segment:

| Correct                 | Wrong (double-prefix)           |
|-------------------------|---------------------------------|
| `services/api-gateway`  | `kv/data/services/api-gateway`  |
| `auth0`                 | `kv/data/auth0`                 |
| `postgres`              | `kv/data/postgres`              |

Using `kv/data/...` makes ESO resolve the real read against `kv/data/data/...`,
which 404s and leaves the ExternalSecret in `SecretSyncedError`.

> Note: this is the **ESO** convention. The Vault **ACL policy** files
> (`helm/vault/policies/*.hcl`) intentionally keep the literal `kv/data/<path>` form,
> because Vault policy paths for a KV v2 engine address the physical `data/` API path.
> The two layers differ on purpose.

After applying:

```bash
kubectl get externalsecret -A           # status should be Ready
kubectl get secret -n ecommerce         # ESO-created Secrets appear here
```

## Migration from current secrets

1. For each existing manually-created `Secret` in `ecommerce/`:
   ```bash
   kubectl get secret <name> -o jsonpath='{.data}' | base64 -d
   ```
2. Write its key/value pairs into Vault: `vault kv put kv/<name> KEY=VALUE ...`.
3. `kubectl apply -f <name>-externalsecret.yaml` — ESO recreates the Secret with the same name. Pods reload on next restart (or via Reloader if installed).
4. Delete the original `Secret` once all consumers are reading the ESO-managed one.

## Decision (finalized)

Secret backend = **HashiCorp Vault (KV v2) fronted by External Secrets Operator (ESO)** — the
platform standard. No cipher-text in git, live rotation, audited reads.

- **Shared datastore/provider secrets** (`postgres`, `mysql`, `mongo`, `auth0`, `stripe`) flow through
  the cluster-scoped `vault-backend` `ClusterSecretStore` (`role: ecommerce-reader`).
- **Per-service secrets** — every backend service has its own `ExternalSecret`
  (`services/<svc>-externalsecret.yaml`) materialising a `<svc>-secret`, sourced through a dedicated
  namespaced `SecretStore` (`services/secretstores.yaml`) that authenticates with a least-privilege
  Vault role `eso-<svc>`. A service reads only its own path plus the datastores it actually uses.

## Production hardening (materialized in `helm/vault/`)

- **HA + Raft** — `helm/vault/values-prod.yaml` (3 replicas, integrated Raft storage, TLS, anti-affinity).
- **Kubernetes auth** (SA JWT, no dev root token) — `helm/vault/bootstrap.sh`.
- **Audit device** — file sink enabled by `bootstrap.sh` (`vault audit enable file`).
- **Per-service least-privilege policies** — `helm/vault/policies/<svc>-policy.hcl`, each bound to an
  `eso-<svc>` Kubernetes-auth role by `bootstrap.sh` and mapped to the matching `SecretStore`.
- Rotation cron on Vault: weekly for DB creds, daily for API keys (operational follow-up).

## Files

| File | Purpose |
|------|---------|
| `vault-cluster-secret-store.yaml` | Cluster-scoped pointer to Vault + auth (shared secrets) |
| `postgres-externalsecret.yaml` | Postgres credentials |
| `mysql-externalsecret.yaml` | MySQL credentials |
| `mongo-externalsecret.yaml` | MongoDB root credentials |
| `auth0-externalsecret.yaml` | Auth0 client + tenant secrets |
| `stripe-externalsecret.yaml` | Stripe API keys (placeholder for §3.9 follow-up) |
| `services/secretstores.yaml` | One least-privilege `SecretStore` per backend service (`eso-<svc>` role) |
| `services/<svc>-externalsecret.yaml` | Per-service `<svc>-secret` (12 services) |
