# Scheduled Tasks Documentation

This document describes all scheduled batch jobs implemented across the e-commerce platform microservices.

## Overview

The platform uses Spring's `@Scheduled` annotation with cron expressions for batch processing. All jobs:
- Have error handling to prevent crashes
- Expose Prometheus metrics for monitoring
- Log start/completion with execution times
- Are configurable via application properties

## Cart Service Jobs

### 1. Expired Cart Cleanup

**Purpose**: Delete carts that have passed their expiration time.

**Schedule**: Daily at 2:00 AM (configurable)

**Configuration**:
```yaml
cart:
  cleanup:
    expired-cron: "0 0 2 * * ?"
```

**Metrics**:
- `cart.cleanup.expired` - Counter of deleted expired carts

**Behavior**:
- Finds carts where `expiresAt < now()` and status is `ACTIVE`
- Deletes matching carts from database
- Logs count of deleted carts

### 2. Abandoned Cart Processing

**Purpose**: Mark inactive carts as abandoned for potential recovery campaigns.

**Schedule**: Daily at 3:00 AM (configurable)

**Configuration**:
```yaml
cart:
  cleanup:
    abandoned-cron: "0 0 3 * * ?"
    abandoned-threshold-hours: 24
```

**Metrics**:
- `cart.cleanup.abandoned` - Counter of carts marked as abandoned

**Behavior**:
- Finds carts not updated for `abandoned-threshold-hours`
- Changes status from `ACTIVE` to `ABANDONED`
- Enables abandoned cart recovery workflows

## Inventory Service Jobs

### 1. Release Expired Reservations

**Purpose**: Auto-release stock reservations that have expired without being committed.

**Schedule**: Every 5 minutes (configurable)

**Configuration**:
```yaml
inventory:
  scheduled:
    expired-reservations-cron: "0 */5 * * * *"
```

**Metrics**:
- `inventory.reservations.expired` - Counter of released reservations
- `inventory.job.release_expired.duration` - Timer for job execution

**Behavior**:
- Finds reservations where `expiresAt < now()` and status is `RESERVED`
- Releases reserved stock back to available inventory
- Updates reservation status to `EXPIRED`

### 2. Cleanup Old Reservations

**Purpose**: Delete old reservation records to manage database size.

**Schedule**: Daily at 2:00 AM (configurable)

**Configuration**:
```yaml
inventory:
  scheduled:
    cleanup-old-reservations-cron: "0 0 2 * * *"
    cleanup-days-threshold: 90
```

**Metrics**:
- `inventory.reservations.cleaned` - Counter of deleted records
- `inventory.job.cleanup_old.duration` - Timer for job execution

**Behavior**:
- Deletes reservations older than `cleanup-days-threshold` days
- Affects all reservation statuses (COMMITTED, RELEASED, EXPIRED)

### 3. Restock Alert Generation

**Purpose**: Generate alerts for items that need restocking.

**Schedule**: Daily at 6:00 AM (configurable)

**Configuration**:
```yaml
inventory:
  scheduled:
    restock-alert-cron: "0 0 6 * * ?"
```

**Metrics**:
- `inventory.restock.alerts` - Counter of alerts generated
- `inventory.low_stock.items` - Gauge of current low stock items
- `inventory.job.restock_alert.duration` - Timer for job execution

**Behavior**:
- Finds items where available quantity <= reorder level
- Logs detailed restock alerts with current stock levels
- Updates monitoring gauge for dashboards

## Order Service Jobs

### 1. Process Abandoned Orders

**Purpose**: Cancel orders stuck in PENDING status.

**Schedule**: Daily at 4:00 AM (configurable)

**Configuration**:
```yaml
order:
  scheduled:
    abandoned-orders-cron: "0 0 4 * * ?"
    abandoned-hours-threshold: 24
```

**Metrics**:
- `order.abandoned.cancelled` - Counter of cancelled orders
- `order.job.process_abandoned.duration` - Timer for job execution

**Behavior**:
- Finds orders with status `PENDING` for longer than threshold
- Updates status to `CANCELLED`
- Releases any associated inventory reservations

### 2. Daily Sales Report

**Purpose**: Generate daily sales metrics for monitoring and analytics.

**Schedule**: Daily at 1:00 AM (configurable)

**Configuration**:
```yaml
order:
  scheduled:
    daily-sales-report-cron: "0 0 1 * * ?"
```

**Metrics**:
- `order.daily.revenue` - Gauge of yesterday's revenue
- `order.daily.count` - Gauge of yesterday's order count
- `order.job.sales_report.duration` - Timer for job execution

**Behavior**:
- Calculates metrics for the previous day
- Aggregates by order status
- Logs summary report
- Updates Prometheus gauges

### 3. Cleanup Completed Orders Data

**Purpose**: Archive or clean up old order data.

**Schedule**: Daily at 3:00 AM (configurable)

**Configuration**:
```yaml
order:
  scheduled:
    cleanup-old-orders-cron: "0 0 3 * * ?"
```

**Behavior**:
- Placeholder for future implementation
- Can archive orders older than X years
- Can remove sensitive data from old orders

## Job Schedule Summary

| Service | Job | Default Schedule | Purpose |
|---------|-----|------------------|---------|
| Cart | Expired Cleanup | 2:00 AM | Delete expired carts |
| Cart | Abandoned Processing | 3:00 AM | Mark inactive carts |
| Inventory | Release Expired | Every 5 min | Release expired reservations |
| Inventory | Cleanup Old | 2:00 AM | Delete old reservation records |
| Inventory | Restock Alerts | 6:00 AM | Generate restock notifications |
| Order | Abandoned Orders | 4:00 AM | Cancel stuck orders |
| Order | Sales Report | 1:00 AM | Generate daily metrics |
| Order | Cleanup Data | 3:00 AM | Archive old orders |

## Monitoring

### Grafana Dashboard

The **Batch Jobs Monitoring** dashboard (`batch-jobs-monitoring.json`) provides:

1. **Cart Service Jobs**
   - Expired carts deleted per hour
   - Abandoned carts marked per hour

2. **Inventory Service Jobs**
   - Expired reservations released
   - Old reservations cleaned
   - Low stock items count (gauge)
   - Restock alerts generated

3. **Order Service Jobs**
   - Abandoned orders cancelled
   - Daily revenue (gauge)
   - Daily order count (gauge)

4. **Job Execution Durations**
   - Average execution time per job
   - Helps identify slow jobs

### Alert Recommendations

Configure alerts for:

1. **Job Failures**
   - Alert if job throws exception (check logs)
   - Alert if job duration exceeds threshold

2. **Business Metrics**
   - High number of abandoned carts
   - Many low stock items
   - Unusual order cancellation rate

3. **System Health**
   - Job not running (missing metrics)
   - Database connection issues

## Configuration Best Practices

1. **Stagger Job Times**: Avoid running multiple jobs at the same time
2. **Off-Peak Hours**: Schedule heavy jobs during low traffic periods
3. **Retry Logic**: Jobs include try-catch to prevent crashes
4. **Idempotency**: Jobs can be safely re-run if needed
5. **Logging**: All jobs log start, completion, and results

## Cron Expression Reference

```
┌───────────── second (0-59)
│ ┌───────────── minute (0-59)
│ │ ┌───────────── hour (0-23)
│ │ │ ┌───────────── day of month (1-31)
│ │ │ │ ┌───────────── month (1-12)
│ │ │ │ │ ┌───────────── day of week (0-6, 0=Sunday)
│ │ │ │ │ │
* * * * * *
```

Examples:
- `0 0 2 * * ?` - Every day at 2:00 AM
- `0 */5 * * * *` - Every 5 minutes
- `0 0 1 * * MON-FRI` - Every weekday at 1:00 AM
