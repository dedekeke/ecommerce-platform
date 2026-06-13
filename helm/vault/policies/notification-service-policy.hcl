# Least-privilege read policy for notification-service (consumed by ESO via role eso-notification-service).
path "kv/data/services/notification-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/notification-service" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
