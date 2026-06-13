# Least-privilege read policy for cart-service (consumed by ESO via role eso-cart-service).
path "kv/data/services/cart-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/cart-service" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
