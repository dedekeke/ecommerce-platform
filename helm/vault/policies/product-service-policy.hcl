# Least-privilege read policy for product-service (consumed by ESO via role eso-product-service).
path "kv/data/services/product-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/product-service" {
  capabilities = ["read", "list"]
}
path "kv/data/mysql" {
  capabilities = ["read"]
}
path "kv/metadata/mysql" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
