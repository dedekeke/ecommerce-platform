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

`vault-cluster-secret-store.yaml` registers a `ClusterSecretStore` that points at the in-cluster Vault. `*-externalsecret.yaml` files declare an `ExternalSecret` per service that materialises a Kubernetes `Secret` named `<svc>-secret` from a Vault path like `kv/data/<svc>`.

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

## Production hardening

- Vault HA mode (≥3 nodes), Raft storage, integrated TLS.
- Vault auth via Kubernetes ServiceAccount JWT (not the dev root token).
- Audit device emitting to Loki for read/write trail.
- Per-service Vault policy with least-privilege paths.
- Rotation cron on Vault: weekly for DB creds, daily for API keys.

## Files

| File | Purpose |
|------|---------|
| `vault-cluster-secret-store.yaml` | Cluster-scoped pointer to Vault + auth |
| `postgres-externalsecret.yaml` | Postgres credentials |
| `mysql-externalsecret.yaml` | MySQL credentials |
| `mongo-externalsecret.yaml` | MongoDB root credentials |
| `auth0-externalsecret.yaml` | Auth0 client + tenant secrets |
| `stripe-externalsecret.yaml` | Stripe API keys (placeholder for §3.9 follow-up) |
