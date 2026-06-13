# Least-privilege read policy for payment-service (consumed by ESO via role eso-payment-service).
path "kv/data/services/payment-service" {
  capabilities = ["read"]
}
path "kv/metadata/services/payment-service" {
  capabilities = ["read", "list"]
}
path "kv/data/postgres" {
  capabilities = ["read"]
}
path "kv/metadata/postgres" {
  capabilities = ["read", "list"]
}
path "kv/data/stripe" {
  capabilities = ["read"]
}
path "kv/metadata/stripe" {
  capabilities = ["read", "list"]
}
path "kv/data/auth0" {
  capabilities = ["read"]
}
path "kv/metadata/auth0" {
  capabilities = ["read", "list"]
}
