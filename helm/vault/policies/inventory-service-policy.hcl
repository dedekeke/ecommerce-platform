# Least-privilege read policy for inventory-service (consumed by ESO via role eso-inventory-service).
path "kv/data/services/inventory-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/inventory-service" {
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
