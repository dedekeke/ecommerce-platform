# Least-privilege read policy for media-service (consumed by ESO via role eso-media-service).
path "kv/data/services/media-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/media-service" {
  capabilities = ["read", "list"]
}
path "kv/data/mongo" {
  capabilities = ["read"]
}
path "kv/metadata/mongo" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
