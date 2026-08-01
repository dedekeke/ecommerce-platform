# Least-privilege read policy for order-service (consumed by ESO via role eso-order-service).
path "kv/data/services/order-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/order-service" {
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

path "kv/data/internal" {
  capabilities = ["read"]
}
path "kv/metadata/internal" {
  capabilities = ["read", "list"]
}
