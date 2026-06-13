# Vault — production HA + per-service least-privilege

Hardened HashiCorp Vault for the ESO secret backend (see `k8s/secrets/README.md` for the wiring).

## Contents

| File | Purpose |
|------|---------|
| `values-prod.yaml` | hashicorp/vault chart values — HA, Raft storage, TLS, audit storage, anti-affinity |
| `bootstrap.sh` | post-init: enable audit device + KV v2 + Kubernetes auth; write per-service policies/roles |
| `policies/<svc>-policy.hcl` | least-privilege read policy per backend service (own path + used datastores) |

## Install

```bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm upgrade --install vault hashicorp/vault \
  -n vault --create-namespace -f helm/vault/values-prod.yaml

# Initialise + unseal the Raft cluster (store the keys in a secure vault, NOT git):
kubectl exec -n vault vault-0 -- vault operator init
kubectl exec -n vault vault-0 -- vault operator unseal <key-shares...>
# (join vault-1, vault-2 then unseal each)

# Configure auth, audit, policies, and per-service roles:
export VAULT_ADDR=https://vault.vault.svc.cluster.local:8200 VAULT_TOKEN=<admin-token>
./helm/vault/bootstrap.sh
```

## Mapping to ESO

`bootstrap.sh` creates one Kubernetes-auth role `eso-<svc>` per service, bound to the
`external-secrets` ServiceAccount and the matching `eso-<svc>` policy. Each role is referenced by a
namespaced `SecretStore` (`k8s/secrets/services/secretstores.yaml`), so a service's `ExternalSecret`
can read only its own Vault paths. Shared datastore secrets continue to use the `ecommerce-reader`
role via the cluster-scoped `vault-backend` store.

## Hardening summary

- HA, Raft, integrated TLS, pod anti-affinity, dedicated audit storage (`values-prod.yaml`).
- Kubernetes SA-JWT auth; dev mode + root token disabled.
- File audit device for a full read/write trail.
- Least-privilege policy + role per service.
