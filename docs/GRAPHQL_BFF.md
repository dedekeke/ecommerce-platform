# GraphQL Backend-For-Frontend

> Single-schema BFF embedded inside the API gateway. Aggregates calls to
> product, cart, order, recommendation and promotion services so that React
> MFEs render a page in **one** HTTP round-trip.

Last updated: 2026-04-29

---

## 1. Why GraphQL here, why now

The catalog page currently fans out to the gateway five times:

| MFE call | Endpoint                                       |
|----------|-------------------------------------------------|
| 1        | `GET /api/products?search=…&page=…`             |
| 2..4     | `GET /api/products/{id}` for each tile detail   |
| 5        | `GET /api/recommendations/product/{id}`         |

Cart and order detail pages have the same shape — N round-trips for N items.
The dominant latency is request setup (TLS, auth, gateway routing), not
backend compute. Collapsing the round-trips at the **edge**, behind one
HTTP call, is the highest-impact MFE perf win in the roadmap (see
[`SCALING_AND_IMPROVEMENTS.md` §4.2](./SCALING_AND_IMPROVEMENTS.md)).

GraphQL is the canonical answer for client-shaped aggregation. **Spring for
GraphQL** is the official Spring project — same team, same release train —
so we get auto-config, schema-mapping inspection, security integration, and
DataLoader support without bringing a third-party server.

---

## 2. Topology decision: inside the gateway, not a separate BFF service

We chose **Option A** — embed GraphQL inside the existing
`infrastructure/api-gateway` module.

| Concern                | Inside gateway (chosen)             | Separate `bff-service`               |
|-----------------------|--------------------------------------|--------------------------------------|
| Number of pods        | 0 new                               | +1                                  |
| JWT validation        | Reuses existing Auth0 chain         | Duplicated config                   |
| Service discovery     | Reuses Eureka load balancer         | Same                                |
| Failure domain        | Same as gateway                     | New                                 |
| Operational surface   | None new                            | Helm chart, dashboards, alerts      |
| Multi-team isolation  | Coupled with gateway team           | Independent                         |

The frontend isn't yet large enough to justify Option B. We can split later
if owning the BFF becomes an organisational necessity — the package layout
(`com.ecommerce.gateway.graphql.*`) is already self-contained.

---

## 3. Schema

Stored in `infrastructure/api-gateway/src/main/resources/graphql/schema.graphqls`.

```graphql
type Query {
  product(id: ID!): Product
  products(category: String, page: Int = 0, size: Int = 20, search: String): ProductPage!
  recommendations(productId: ID!, limit: Int = 10): [Product!]!
  cart(userId: ID!): Cart
  order(id: ID!): Order
  myOrders(userId: ID!, page: Int = 0, size: Int = 20): OrderPage!
}
```

Highlights:

- `Product.recommendations(limit)` is a **nested resolver** — fetches from
  recommendation-service then hydrates each product id via the DataLoader.
- `Product.liveStockQty` is intentionally nullable. Until the SSE stream
  from inventory-service is wired up, the resolver returns the
  `stockQuantity` snapshot. UI falls back to `inStock` when null.
- `CartItem.product` and `OrderItem.product` are **batch resolvers** — N
  items in a cart trigger one `findByIds(...)` call against product-service.

The full schema is also reproduced inline in this file's appendix.

---

## 4. DataLoader rationale

Without batching, a query like

```graphql
{ myOrders(userId:"u") { content { items { product { name } } } } }
```

generates one product-service call per `OrderItem`. Order with 5 items =
5 calls. 100 orders × 5 items = 500 calls. The page becomes a per-user
DDoS on the product service.

With the `productByIdLoader` registered against
`org.springframework.graphql.execution.BatchLoaderRegistry`:

1. Each `OrderItem.product` resolver calls `loader.load(productId)` —
   non-blocking, returns a `CompletableFuture`.
2. Spring for GraphQL waits until **all** field resolvers in the current
   execution depth have queued their keys.
3. The registry dispatches **once** with the full key set to
   `ProductBackendClient.findByIds(List<String>)`.
4. The result map is fanned back out to the futures.

**Verified by `RecommendationDataLoaderTest`.** A query touching 5 items
records exactly 1 call into `findByIds`, 0 calls into `findById`.

The same loader name is used by `Product.recommendations`,
`CartItem.product`, and `OrderItem.product` — so a query that mixes all
three still dispatches a single batch.

### Naming convention (gotcha)

Spring's `DataLoaderMethodArgumentResolver` matches on either:
- the **method parameter name** (e.g. `productByIdLoader`), or
- the **value type's class name** (e.g. `ProductDto`).

We register the loader as `productByIdLoader` to keep the parameter name
in resolvers and the registration name aligned. Renaming the value DTO
would not silently break the binding, because the parameter name still
matches.

---

## 5. Sample queries

### Catalog page (single round-trip)

```graphql
query CatalogPage($q: String, $page: Int, $size: Int) {
  products(search: $q, page: $page, size: $size) {
    totalElements
    pageNumber
    content {
      id
      name
      price
      currency
      images
      inStock
      liveStockQty
      recommendations(limit: 5) { id name price images }
    }
  }
}
```

### Cart page

```graphql
query CartPage($userId: ID!) {
  cart(userId: $userId) {
    subtotal
    itemCount
    appliedPromotions { code description discountAmount }
    items {
      productId
      quantity
      unitPrice
      subtotal
      product { id name images inStock }
    }
  }
}
```

### Order detail

```graphql
query OrderDetail($id: ID!) {
  order(id: $id) {
    orderNumber
    status
    totalAmount
    createdAt
    items {
      productId
      quantity
      unitPrice
      product { id name images }
    }
  }
}
```

---

## 6. Auth surface

| Query              | Auth required? |
|--------------------|----------------|
| `product`          | No             |
| `products`         | No             |
| `recommendations`  | No             |
| `cart`             | Yes (JWT)      |
| `order`            | Yes (JWT)      |
| `myOrders`         | Yes (JWT)      |

Enforcement is two-layer:

1. **HTTP layer.** `SecurityConfig` permits `/graphql` so the request reaches
   the resolver. (Otherwise an unauthenticated client would never see
   field-level errors — the gateway would 401 the whole POST.)
2. **Resolver layer.** `@PreAuthorize("isAuthenticated()")` on the
   authenticated queries. Anonymous principals trigger an
   `AccessDeniedException` that Spring for GraphQL maps to a top-level
   GraphQL error with `errorType: FORBIDDEN`.

Method security is enabled by `GraphQlSecurityConfig` via
`@EnableReactiveMethodSecurity`.

The userId-vs-JWT-subject check still lives in the downstream services —
the BFF does not yet enforce that `cart(userId: $x)` matches the JWT
`sub` claim. See **Open follow-ups** below.

---

## 7. Federation considerations

We are **not** using Apollo Federation.

Reasons:
- One BFF, one schema. There is no second graph to federate yet.
- Federation introduces a router (Apollo or cosmo-router) which is a new
  pod — exactly what we tried to avoid by embedding the BFF in the gateway.
- The current ownership model has a single backend team; a federated
  super-graph pays off when separate teams own separate sub-graphs.
- Subscription routing across federated services is non-trivial —
  deferring this until we add real-time features (live cart, live
  inventory) gives us time to evaluate.

When we **would** federate:
- A third frontend lands with materially different data needs (e.g. a
  vendor portal whose graph overlaps the customer graph).
- Catalog + reviews + ranking become independently owned (the
  `review-service` planned in the roadmap may trigger this).

---

## 8. Open follow-ups

- **Subscriptions** for live cart and live inventory. Spring for GraphQL
  supports WebSocket subscriptions but the current MFE shell does not
  yet have a WS client. Track in the roadmap.
- **userId / JWT subject binding.** Move the "cart belongs to caller"
  check into the resolver so we can return `FORBIDDEN` rather than
  bouncing through the downstream service.
- **Apollo Federation** — re-evaluate when the second BFF lands.
- **Persisted queries** to lock down the public schema in production.
- **Query complexity / depth limits** to defend against malicious queries.

---

## Appendix: full schema

See `infrastructure/api-gateway/src/main/resources/graphql/schema.graphqls`.
