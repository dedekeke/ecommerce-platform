# TLS — cert-manager + Let's Encrypt

> Back to [k8s README](../README.md).

## Decision

TLS certificates are issued automatically by **cert-manager** from **Let's Encrypt** via the
HTTP-01 solver on the nginx ingress. No certificates or private keys live in git. The platform
Ingress (`k8s/base/ingress/ingress.yaml`) is annotated `cert-manager.io/cluster-issuer: letsencrypt-prod`
and references the `ecommerce-tls` Secret that cert-manager populates.

## Environment-driven values

| Variable | Used in | Purpose |
|----------|---------|---------|
| `ACME_EMAIL` | `cluster-issuer.yaml` | Let's Encrypt account / expiry-notice contact |
| `BASE_DOMAIN` | `certificate.yaml`, ingress | apex + `api.` SAN for the cert |

## Install

```bash
# cert-manager (CRDs + controller)
helm repo add jetstack https://charts.jetstack.io
helm upgrade --install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace --set crds.enabled=true

# Issuers + certificate (env-driven; envsubst renders ${ACME_EMAIL} / ${BASE_DOMAIN})
export ACME_EMAIL=ops@yourcompany.tld BASE_DOMAIN=shop.yourcompany.tld
for f in cluster-issuer.yaml certificate.yaml; do envsubst < $f; done | kubectl apply -f -
```

## Files

| File | Purpose |
|------|---------|
| `cluster-issuer.yaml` | `letsencrypt-prod` + `letsencrypt-staging` ClusterIssuers (ACME, HTTP-01) |
| `certificate.yaml` | `ecommerce-tls` Certificate for `${BASE_DOMAIN}` + `api.${BASE_DOMAIN}` |

## Notes

- Use `letsencrypt-staging` first to avoid hitting LE rate limits while validating DNS/ingress;
  switch the ingress/certificate `issuerRef` to `letsencrypt-prod` once issuance succeeds.
- Helm path: the umbrella chart sets the same annotation via `global.ingress.certManager.clusterIssuer`.
