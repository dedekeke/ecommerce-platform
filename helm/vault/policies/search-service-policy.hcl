# Least-privilege read policy for search-service (consumed by ESO via role eso-search-service).
path "kv/data/services/search-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/search-service" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
