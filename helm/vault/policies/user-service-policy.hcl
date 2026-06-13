# Least-privilege read policy for user-service (consumed by ESO via role eso-user-service).
path "kv/data/services/user-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/user-service" {
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
