# Least-privilege read policy for Kafka admin SASL credentials (consumed by ESO via role eso-kafka).
path "kv/data/kafka" {
  capabilities = ["read"]
}
path "kv/metadata/kafka" {
  capabilities = ["read", "list"]
}
