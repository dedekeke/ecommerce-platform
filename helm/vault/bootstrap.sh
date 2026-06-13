#!/usr/bin/env bash
# Vault production bootstrap: audit device, KV v2, Kubernetes auth, and per-service
# least-privilege policies/roles mapped to the External Secrets Operator (ESO) ServiceAccount.
#
# Run AFTER `vault operator init` + `vault operator unseal` against an HA Raft cluster.
# Requires: VAULT_ADDR, VAULT_TOKEN (a privileged token, not the root token in steady state).
#
#   export VAULT_ADDR=https://vault.vault.svc.cluster.local:8200
#   export VAULT_TOKEN=<admin-token>
#   ./helm/vault/bootstrap.sh
set -euo pipefail

POLICY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/policies" && pwd)"

# ESO ServiceAccount that authenticates to Vault (matches ClusterSecretStore auth).
ESO_SA_NAME="${ESO_SA_NAME:-external-secrets}"
ESO_SA_NAMESPACE="${ESO_SA_NAMESPACE:-external-secrets}"

# 1) Audit device — file sink (collected from stdout/sidecar to the log pipeline).
if ! vault audit list 2>/dev/null | grep -q '^file/'; then
  vault audit enable file file_path=/vault/audit/audit.log
fi

# 2) KV v2 secrets engine at kv/.
if ! vault secrets list 2>/dev/null | grep -q '^kv/'; then
  vault secrets enable -path=kv -version=2 kv
fi

# 3) Kubernetes auth backend.
if ! vault auth list 2>/dev/null | grep -q '^kubernetes/'; then
  vault auth enable kubernetes
fi
vault write auth/kubernetes/config \
  kubernetes_host="https://kubernetes.default.svc:443" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# 4) Per-service least-privilege policies + roles bound to the ESO ServiceAccount.
#    ESO presents the external-secrets SA JWT; each role grants exactly one service policy.
for f in "$POLICY_DIR"/*-policy.hcl; do
  svc="$(basename "$f" -policy.hcl)"
  vault policy write "eso-${svc}" "$f"
  vault write "auth/kubernetes/role/eso-${svc}" \
    bound_service_account_names="${ESO_SA_NAME}" \
    bound_service_account_namespaces="${ESO_SA_NAMESPACE}" \
    policies="eso-${svc}" \
    ttl=1h
  echo "configured policy + role eso-${svc}"
done

echo "Vault bootstrap complete."
