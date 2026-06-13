# Least-privilege read policy for promotion-service (consumed by ESO via role eso-promotion-service).
path "kv/data/services/promotion-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/promotion-service" {
  capabilities = ["read", "list"]
}
path "kv/data/postgres" {
  capabilities = ["read"]
}
path "kv/metadata/postgres" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
