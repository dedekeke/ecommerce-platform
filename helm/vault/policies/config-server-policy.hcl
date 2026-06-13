# Least-privilege read policy for config-server (consumed by ESO via role eso-config-server).
path "kv/data/services/config-server" {
  capabilities = ["read"]
}
path "kv/metadata/services/config-server" {
  capabilities = ["read", "list"]
}
