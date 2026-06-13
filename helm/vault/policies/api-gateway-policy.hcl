# Least-privilege read policy for api-gateway (consumed by ESO via role eso-api-gateway).
path "kv/data/services/api-gateway" {
  capabilities = ["read"]
}
path "kv/metadata/services/api-gateway" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
