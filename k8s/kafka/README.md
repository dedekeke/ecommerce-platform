# Kafka Broker ACLs

> Back to [k8s README](../README.md).

Least-privilege Kafka authorization: each service authenticates with its own **SASL principal**
(`User:<service>`) and is allowed to **produce only to the topic prefixes it owns** and consume only
the prefixes it depends on. This prevents a compromised or buggy service from writing to another
domain's event stream.

## Prerequisites (broker side)

The broker must run with an authorizer and SASL enabled, e.g.:

```properties
authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer   # KRaft
# (or kafka.security.authorizer.AclAuthorizer for ZooKeeper-based clusters)
allow.everyone.if.no.acl.found=false
super.users=User:admin
listener.name.sasl_ssl.sasl.enabled.mechanisms=PLAIN
```

The ACL init Job connects over `SASL_SSL` and verifies the broker cert against the CA in
Secret `kafka-tls` (key `ca.crt`, mounted at `/etc/kafka/tls/ca.crt`); create it before applying.

Each service sets its client SASL identity (`User:<service>`) via its own credentials (delivered by
ESO). Producer/consumer client config is out of scope here — this directory only manages broker ACLs.

## Produce ownership (WRITE, prefixed)

| Principal | Owned topic prefixes |
|-----------|----------------------|
| `order-service` | `order.` |
| `payment-service` | `payment.`, `refund.` |
| `product-service` | `product.` |
| `inventory-service` | `stock.`, `inventory-updated.` |
| `promotion-service` | `promotion.` |
| `cart-service` | `cart.` |
| `search-service` | `search.` |
| `notification-service` | `notification.` |

Consume (READ) grants are in `kafka-acls-job.yaml` (`consume.map`); each service also gets READ on its
own consumer-group prefix.

## Apply

```bash
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
kubectl apply -f kafka-externalsecret.yaml          # admin SASL creds via Vault/ESO
envsubst < kafka-acls-job.yaml | kubectl apply -f -  # one-shot ACL init Job
kubectl logs -n ecommerce job/kafka-acls-init        # verify "ACLs applied"
```

## Files

| File | Purpose |
|------|---------|
| `kafka-acls-job.yaml` | ConfigMap (produce/consume maps) + init Job running `kafka-acls.sh` |
| `kafka-externalsecret.yaml` | Admin SASL credentials (`kafka-secret`) via Vault/ESO |

## Updating ACLs

Edit the `produce.map` / `consume.map` ConfigMap entries and re-run the Job. `kafka-acls --add` is
idempotent; remove stale grants with `kafka-acls --remove`.
