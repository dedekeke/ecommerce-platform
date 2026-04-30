# Backup & Disaster Recovery

## Targets

| Metric | Target | Notes                                                          |
|--------|--------|----------------------------------------------------------------|
| RTO    | 4 h    | Time to restore service after a region-level outage            |
| RPO    | 1 h    | Maximum acceptable data loss between backup and incident       |
| Hot data backup cadence | 1 h | Postgres / MySQL / Mongo via WAL / binlog / oplog tail |
| Cold full snapshot      | 24 h | pg_dump / mysqldump / mongodump → S3 |
| Backup retention        | 30 days hot, 1 year cold | Lifecycle on S3 bucket |

## Coverage

| Datastore     | Method                       | Schedule | Manifest                                |
|---------------|------------------------------|----------|-----------------------------------------|
| PostgreSQL    | `pg_dump -Fc \| gzip` → S3   | 02:00 UTC daily | `k8s/base/cronjobs/postgres-backup.yaml` |
| MySQL         | `mysqldump --single-transaction` → S3 | 03:00 UTC daily | `k8s/base/cronjobs/mysql-backup.yaml`    |
| MongoDB       | `mongodump --gzip` → S3      | 02:30 UTC daily | `k8s/base/cronjobs/mongodb-backup.yaml`  |
| Redis         | AOF (everysec) + RDB snapshot every 5 min | continuous | configured in `docker-compose.prod.yml`; treat Redis as cache, not source-of-truth |
| Elasticsearch | snapshot repository → S3     | 04:00 UTC daily | TODO — add CronJob; uses `_snapshot` API |
| Kafka         | MirrorMaker 2.0 to DR cluster | continuous | TODO — out of scope for this round; document offsets recovery via consumer reset |
| Object storage (media-service) | S3 Cross-Region Replication | continuous | enable on bucket; no app code |

### Redis persistence — already configured?

`docker-compose.yml` (dev) sets `redis-server --appendonly yes`. Production
compose (`docker-compose.prod.yml`) goes further with `--appendfsync everysec`
and explicit RDB save points. Verified.

## Storage

- **Primary cold-backup bucket**: `s3://ecommerce-backups-prod` (placeholder).
- **DR replica**: `s3://ecommerce-backups-prod-dr` in a different AWS region.
- Bucket policy:
  - Versioning ON
  - Lifecycle: hot tier 30 d → Glacier 1 y → expire
  - Encryption: SSE-KMS with customer-managed key
  - Block all public access

Credentials are stored in the Kubernetes secret `backup-secrets` — see
`k8s/README.md` for the `kubectl create secret` command.

## Restore Procedures

### PostgreSQL

```bash
# 1. Pull the latest dump
aws s3 cp s3://ecommerce-backups-prod/backups/postgres/orderdb/20260428T020000Z.dump.gz - \
  | gunzip > /tmp/orderdb.dump

# 2. Stop the service that owns the DB (so the schema isn't being written to)
kubectl -n ecommerce-prod scale deploy/order-service --replicas=0

# 3. Drop & recreate the target DB
kubectl -n ecommerce-prod exec -it postgres-0 -- \
  psql -U $POSTGRES_USER -c "DROP DATABASE orderdb; CREATE DATABASE orderdb;"

# 4. Restore
kubectl -n ecommerce-prod exec -i postgres-0 -- \
  pg_restore -U $POSTGRES_USER -d orderdb < /tmp/orderdb.dump

# 5. Restart consumers
kubectl -n ecommerce-prod scale deploy/order-service --replicas=2
```

### MySQL

```bash
aws s3 cp s3://ecommerce-backups-prod/backups/mysql/productdb/20260428T030000Z.sql.gz - \
  | gunzip \
  | kubectl -n ecommerce-prod exec -i mysql-0 -- mysql -u$MYSQL_USER -p"$MYSQL_PASSWORD" productdb
```

### MongoDB

```bash
aws s3 cp s3://ecommerce-backups-prod/backups/mongodb/20260428T023000Z.gz /tmp/mongo.gz
kubectl -n ecommerce-prod cp /tmp/mongo.gz mongodb-0:/tmp/mongo.gz
kubectl -n ecommerce-prod exec -it mongodb-0 -- \
  mongorestore --uri="mongodb://$MONGO_USER:$MONGO_PASSWORD@localhost:27017/?authSource=admin" \
               --gzip --archive=/tmp/mongo.gz --drop
```

### Redis

Production Redis runs with AOF + RDB. To restore from snapshot:

```bash
# 1. Stop Redis
kubectl -n ecommerce-prod scale sts/redis --replicas=0

# 2. Replace dump.rdb / appendonly.aof on the PVC (mount, copy, unmount)
# 3. Start Redis - it will load AOF on boot
kubectl -n ecommerce-prod scale sts/redis --replicas=1
```

### Elasticsearch

```bash
# Register snapshot repo (one-time)
curl -u elastic:$ELASTIC_PASSWORD -X PUT \
  "http://elasticsearch:9200/_snapshot/s3_backups" \
  -H 'Content-Type: application/json' \
  -d '{"type":"s3","settings":{"bucket":"ecommerce-backups-prod","base_path":"elasticsearch"}}'

# Restore the latest snapshot
curl -u elastic:$ELASTIC_PASSWORD -X POST \
  "http://elasticsearch:9200/_snapshot/s3_backups/snapshot_2026_04_28/_restore"
```

## DR Table-Top Exercise Template

Run quarterly. Capture findings in `docs/dr-runs/YYYY-MM-DD.md`.

```
Date / Lead / Participants:

Scenario (pick one):
  □ Region-level outage (primary cloud region down)
  □ Postgres data corruption (silent — discovered 4h later)
  □ Ransomware on backup bucket (need to use DR replica)
  □ Kafka cluster failure (need to fail over to DR cluster)
  □ Compromised secret (rotate everything; restart all services)

Pre-flight:
  □ All participants have prod/staging access
  □ Latest backup verified (timestamp:        )
  □ DR runbook reviewed
  □ Rollback plan agreed

Execution timeline:
  T+0min   Incident declared
  T+__min  Backup identified for restore
  T+__min  Restore initiated
  T+__min  Service health green
  T+__min  Smoke tests passing
  T+__min  Stakeholders notified

Post-mortem:
  - Did we hit RTO (≤ 4h)?
  - Did we hit RPO (≤ 1h data loss)?
  - What broke that wasn't expected?
  - Action items (owner, due date):
    1.
    2.
```

## Open items

- Add Elasticsearch snapshot CronJob (currently documented but not provisioned).
- Configure Kafka MirrorMaker 2.0 to a DR cluster.
- Enable S3 cross-region replication on the backup bucket.
- Run first DR table-top within 30 days of go-live.
