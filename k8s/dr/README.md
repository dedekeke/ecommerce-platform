# Disaster Recovery (DR)

> Back to [k8s README](../README.md).

Cross-region resilience for the two stateful stores that the daily DB backups
(`k8s/base/cronjobs/*`) do not cover: **Elasticsearch** (search index) and **Kafka** (event log).

## Components

| File | What | Mechanism |
|------|------|-----------|
| `elasticsearch-snapshot.yaml` | ES snapshot repo + daily snapshot | S3 snapshot repository, CronJob `_snapshot` API @ 03:00 UTC |
| `kafka-mirrormaker2.yaml` | Kafka topic replication to DR cluster | Strimzi `KafkaMirrorMaker2`, IdentityReplicationPolicy (active/passive) |

## Env-driven endpoints

| Variable | Used by |
|----------|---------|
| `ES_SNAPSHOT_BUCKET`, `AWS_S3_REGION` | ES snapshot repository |
| `KAFKA_SOURCE_BOOTSTRAP` | MM2 source cluster |
| `KAFKA_DR_BOOTSTRAP` | MM2 DR (target) cluster |

## Apply

```bash
export ES_SNAPSHOT_BUCKET=ecommerce-dr AWS_S3_REGION=us-east-1 \
       KAFKA_SOURCE_BOOTSTRAP=kafka:9092 KAFKA_DR_BOOTSTRAP=kafka-dr.dr-region:9092
for f in elasticsearch-snapshot.yaml kafka-mirrormaker2.yaml; do envsubst < $f; done | kubectl apply -f -
```

## Prerequisites

- **Elasticsearch**: the `repository-s3` plugin installed and S3 credentials available to ES nodes
  (IRSA / instance profile, or `s3.client.default.*` keystore entries).
- **Kafka MM2**: the **Strimzi** operator installed (provides the `KafkaMirrorMaker2` CRD) and a
  reachable DR Kafka cluster.

## Failover notes

- MM2 uses `IdentityReplicationPolicy` so topic names are identical on both clusters; consumers
  repoint to `KAFKA_DR_BOOTSTRAP` without renaming topics. The checkpoint connector syncs consumer
  group offsets so consumers resume near where they left off.
- Restore ES from the latest snapshot: `POST _snapshot/ecommerce-dr/<snap>/_restore`.
