# Service-Level Objectives

> Each row defines the SLI (what is measured), SLO (target), 30-day error
> budget (max allowed bad minutes), and the burn-rate alert thresholds we
> page / ticket on. SLIs use the Spring Boot Actuator
> `http_server_requests_seconds_count` metric; availability is the
> proportion of requests with `outcome="SUCCESS"` over the rolling window.
>
> Multi-window multi-burn-rate alerting follows the Google SRE Workbook
> pattern — fast burn (1h, 14.4×) catches rapid budget exhaustion (alert if
> the last hour's error rate would burn 30 days of budget in <2 days).
> Slow burn (6h, 6×) catches chronic regressions.
>
> Error-budget math: `budget_minutes = (1 - SLO) * 30 * 24 * 60`.

## Availability + Latency Matrix

| # | Service | SLI (good / total) | Availability SLO | Latency p95 SLO | 30-day error budget | Fast burn (1h, 14.4×) | Slow burn (6h, 6×) |
|---|---------|--------------------|------------------|------------------|----------------------|------------------------|--------------------|
| 1 | api-gateway | success-rate of edge requests | 99.9% | <500 ms | 43.2 min | error_rate > 0.0144 (1.44%) for 2m -> page | error_rate > 0.006 (0.6%) for 15m -> ticket |
| 2 | product-service | success-rate of catalogue reads/writes | 99.95% | <300 ms | 21.6 min | error_rate > 0.0072 (0.72%) for 2m -> page | error_rate > 0.003 (0.3%) for 15m -> ticket |
| 3 | order-service | success-rate of order CRUD + saga orchestration | 99.9% | <800 ms | 43.2 min | error_rate > 0.0144 for 2m -> page | error_rate > 0.006 for 15m -> ticket |
| 4 | payment-service | success-rate of payment intent / capture / refund | 99.95% | <1000 ms | 21.6 min | error_rate > 0.0072 for 2m -> page | error_rate > 0.003 for 15m -> ticket |
| 5 | cart-service | success-rate of cart CRUD | 99.9% | <200 ms | 43.2 min | error_rate > 0.0144 for 2m -> page | error_rate > 0.006 for 15m -> ticket |
| 6 | inventory-service | success-rate of stock check / reservation | 99.9% | <400 ms | 43.2 min | error_rate > 0.0144 for 2m -> page | error_rate > 0.006 for 15m -> ticket |
| 7 | search-service | success-rate of search queries | 99.5% | <400 ms | 216 min | error_rate > 0.072 (7.2%) for 2m -> page | error_rate > 0.030 (3%) for 15m -> ticket |
| 8 | notification-service | success-rate of email/notification delivery | 99.0% | n/a (async) | 432 min | error_rate > 0.144 for 2m -> page | error_rate > 0.060 for 15m -> ticket |
| 9 | promotion-service | success-rate of promotion evaluation | 99.9% | <300 ms | 43.2 min | error_rate > 0.0144 for 2m -> page | error_rate > 0.006 for 15m -> ticket |
| 10 | user-service | success-rate of auth / profile reads/writes | 99.9% | <300 ms | 43.2 min | error_rate > 0.0144 for 2m -> page | error_rate > 0.006 for 15m -> ticket |
| 11 | media-service | success-rate of media upload / fetch | 99.0% | n/a (best-effort) | 432 min | error_rate > 0.144 for 2m -> page | error_rate > 0.060 for 15m -> ticket |

### Error budget formula

For SLO target `S` (e.g. 0.999), the maximum tolerated error rate is
`1 - S`. A multi-window burn-rate alert with multiplier `M` triggers when
the observed error rate over the short window exceeds `M * (1 - S)`. The
classic Google SRE pairs are:

- **Fast burn**: 14.4× over 1h -> exhausts a 30d budget in ~2 days. Page.
- **Slow burn**:  6×  over 6h -> exhausts a 30d budget in ~5 days. Ticket.

## SLI definition (Prometheus)

The numerator is "good" requests; the denominator is "total" requests:

```promql
# error rate (1h window)
sum(rate(http_server_requests_seconds_count{application="$svc",outcome!="SUCCESS"}[1h]))
/
sum(rate(http_server_requests_seconds_count{application="$svc"}[1h]))
```

`outcome` is a built-in Spring Boot Actuator tag with values `SUCCESS`,
`CLIENT_ERROR`, `SERVER_ERROR`, `INFORMATIONAL`, `REDIRECTION`, `UNKNOWN`.
We treat anything other than `SUCCESS` as a budget-burning event.

## Latency SLI (informational)

Latency thresholds in the matrix above are tracked separately — they are
**not** wired into burn-rate alerting in this iteration. To extend, query
the percentile estimate from the same metric family:

```promql
histogram_quantile(0.95,
  sum by (le, application) (rate(http_server_requests_seconds_bucket{application="$svc"}[5m]))
) > 0.5  # p95 > 500 ms
```

## Review cadence

- Quarterly SLO review: tighten / relax based on observed reality.
- Monthly error-budget burn-down review with engineering leads.
- Page on fast-burn only; slow-burn opens a Jira ticket via Alertmanager
  webhook routing. Severity routing lives in Alertmanager config (out of
  scope for this commit).

## References

- Google SRE Workbook, *Implementing SLOs*: https://sre.google/workbook/implementing-slos/
- Multi-window multi-burn-rate alerting: https://sre.google/workbook/alerting-on-slos/
