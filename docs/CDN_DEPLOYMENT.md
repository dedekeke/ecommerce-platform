# CDN Deployment Strategy

> Status: configuration only. No CDN provider provisioned yet.

## What goes behind the CDN

| Asset | Source | Cacheable? | Why |
|---|---|---|---|
| `dist/assets/*-[hash].js` | shell-app, product-catalog-mfe builds | yes — 1 year, immutable | Content-addressed; never reused for different bytes. |
| `dist/assets/*-[hash].css` | same | yes — 1 year, immutable | Same. |
| `dist/assets/*-[hash].(woff2\|svg\|png)` | same | yes — 1 year, immutable | Same. |
| `index.html` | shell-app | no — `no-store, must-revalidate` | Entry point that references the latest hashed bundles. |
| `assets/remoteEntry.js` | each MFE | short — 60 s | Federation manifest can change at deploy without a filename change. |
| Product images (`/api/media/...`) | media-service via S3 | yes — 30 days, `stale-while-revalidate` | User-uploaded; immutable per upload. Use signed URLs for private media. |
| API responses (`/api/...`) | gateway | no — bypass CDN | Authenticated, dynamic. |

## Provider — placeholder

We are **not** yet committed to a provider. Defer the choice until traffic patterns are known. Sketch configs:

### CloudFront (placeholder)

```hcl
# infra/terraform/cloudfront.tf  (TODO — not yet checked in)
resource "aws_cloudfront_distribution" "shell" {
  origin {
    domain_name = "static-origin.example.com"   # nginx host serving /infrastructure/cdn/nginx-static.conf
    origin_id   = "shell-static"
  }
  default_cache_behavior {
    target_origin_id       = "shell-static"
    viewer_protocol_policy = "redirect-to-https"
    cached_methods         = ["GET", "HEAD"]
    forwarded_values { query_string = false }
    # TTLs are overridden per-path by the Cache-Control headers
    # emitted by nginx, so we leave default_ttl/min_ttl/max_ttl
    # at their defaults and let the origin drive.
  }
}
```

### Cloudflare (placeholder)

Page Rule on `*.example.com/assets/*`:
- Cache Level: Cache Everything
- Edge Cache TTL: 1 year
- Browser Cache TTL: Respect Existing Headers

Page Rule on `*.example.com/index.html` and `*.example.com/`:
- Cache Level: Bypass

## Cache invalidation on deploy

Because hashed assets are immutable, a deploy **does not require an invalidation** of `/assets/*`. Only `index.html` (and `remoteEntry.js`, capped at 60 s TTL anyway) need to be flushed.

CI deploy step:

```bash
# After uploading the new dist/ to the static origin:
aws cloudfront create-invalidation \
  --distribution-id $CF_DIST_ID \
  --paths "/index.html" "/assets/remoteEntry.js"
```

This is fast (< 1 s) and free within free-tier limits. Avoid the `/*` shotgun — it's slow and counts against the paid invalidation quota.

## Real-domain TODOs

- [ ] Decide CloudFront vs Cloudflare vs Fastly. Cloudflare is the default unless we already have AWS-heavy ops.
- [ ] Provision the static origin (likely an S3 bucket fronted by the nginx config above, or an nginx pod in the existing k8s cluster).
- [ ] Wire DNS: `static.example.com` → CDN distribution.
- [ ] Add CSP `script-src https://static.example.com 'self'` once domain is final.
- [ ] Add `Subresource Integrity` (SRI) hashes for the federation manifest so a CDN compromise can't silently swap MFE code.
- [ ] Set up access logs → S3 → Athena (or Cloudflare Logpush) for cache-hit-rate observability.
