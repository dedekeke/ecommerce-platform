# KEDA Kafka-Lag Autoscaling

KEDA (Kubernetes Event-Driven Autoscaler) drives `notification-service`,
`search-service`, and `order-service` replica counts based on Kafka consumer
lag rather than CPU. Manifests live in `k8s/base/scaling/`. Per-environment
maxReplicaCount overrides live in `k8s/overlays/{staging,production}/scaling/`.

## 1. Install the KEDA operator

KEDA runs as a cluster-scoped operator in its own namespace.

```bash
kubectl apply -f https://github.com/kedacore/keda/releases/download/v2.14.0/keda-2.14.0.yaml
```

This installs the `keda` namespace, the `ScaledObject` / `ScaledJob` /
`TriggerAuthentication` CRDs, and the `keda-operator` and
`keda-metrics-apiserver` Deployments.

## 2. Verify the operator is up

```bash
kubectl -n keda get pods
# Expect: keda-operator-xxx and keda-operator-metrics-apiserver-xxx both Running
```

```bash
kubectl get crd | grep keda.sh
# Expect: scaledobjects.keda.sh, scaledjobs.keda.sh, triggerauthentications.keda.sh
```

## 3. Apply the platform ScaledObjects

```bash
kubectl apply -k k8s/base                    # cluster-wide / dev defaults
kubectl apply -k k8s/overlays/staging        # caps maxReplicas to 2
kubectl apply -k k8s/overlays/production     # max 6 (notification) / 4 (search, order)
```

## 4. Inspect ScaledObject status

```bash
kubectl -n ecommerce get scaledobject
# NAME                            SCALETARGETKIND   SCALETARGETNAME       MIN   MAX   READY   ACTIVE
# notification-service-kafka-lag  Deployment        notification-service  1     6     True    False
# order-service-kafka-lag         Deployment        order-service         1     4     True    False
# search-service-kafka-lag        Deployment        search-service        1     4     True    False
```

```bash
kubectl -n ecommerce describe scaledobject notification-service-kafka-lag
# Look at: Status.Conditions (Ready=True), Status.ExternalMetricNames, Events
```

KEDA also creates a managed HPA per ScaledObject. View it with:

```bash
kubectl -n ecommerce get hpa | grep keda-hpa
```

## 5. Trigger lag manually for testing

Produce 100 messages to a watched topic without any consumer running. KEDA
will observe the lag through the configured `consumerGroup` and scale the
target Deployment up to `maxReplicaCount`.

```bash
# 1. Scale the consumer to zero so messages accumulate
kubectl -n ecommerce scale deployment/notification-service --replicas=0

# 2. Publish 100 messages to order.created (using the kafka-console-producer
#    bundled in the kafka image). Adjust the broker DNS as appropriate.
kubectl -n ecommerce exec -it deploy/kafka -- \
  bash -c "for i in {1..100}; do echo '{\"orderId\":'\$i'}'; done | \
    kafka-console-producer --bootstrap-server kafka:9092 --topic order.created"

# 3. Watch KEDA observe the lag and ramp the deployment back up
kubectl -n ecommerce get scaledobject notification-service-kafka-lag -w
kubectl -n ecommerce get hpa keda-hpa-notification-service-kafka-lag -w
```

Within `pollingInterval` (15s) KEDA should report the lag and request more
replicas. Once the consumer drains the topic, lag returns to zero and the
deployment scales back down after the `cooldownPeriod` (300s).

## Caveats

- KEDA's Kafka scaler reads `__consumer_offsets`. The `consumerGroup`
  specified in each trigger must match the `spring.kafka.consumer.group-id`
  of the deployment exactly. If the consumer group has never committed an
  offset (i.e. the service has never started), KEDA reports
  `lagThreshold` not exceeded and the deployment stays at `minReplicaCount`.
- `offsetResetPolicy: latest` mirrors the Spring Kafka default and tells
  KEDA how to compute lag for partitions with no committed offset.
- `cooldownPeriod: 300s` is intentionally long to absorb traffic bursts; do
  not lower it without first confirming there is no flapping.
