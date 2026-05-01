# Session Summary — 2026-04-29 — GraphQL BFF

## Goal

Add a GraphQL backend-for-frontend on the API gateway so the React MFEs can
fetch a page in one round-trip. From SCALING_AND_IMPROVEMENTS.md §4.2.

## What was built

**Branch:** `feature/graphql-bff`. No push, no merge.

- `infrastructure/api-gateway/pom.xml` — added
  `spring-boot-starter-graphql`, `spring-boot-starter-webflux` (explicit),
  `spring-graphql-test`, `mockwebserver`. Wired the JaCoCo plugin so the
  graphql package coverage shows up in reports.
- `src/main/resources/graphql/schema.graphqls` — single schema:
  `product`, `products`, `recommendations`, `cart`, `order`, `myOrders` +
  the nested `Product.recommendations`, `CartItem.product`,
  `OrderItem.product` resolvers.
- `com.ecommerce.gateway.graphql.config.BffWebClientConfig` —
  `@LoadBalanced WebClient.Builder` and per-service WebClients pointing at
  `lb://product-service`, etc.
- `com.ecommerce.gateway.graphql.client.*` — thin reactive clients over
  product / cart / order / recommendation services.
- `com.ecommerce.gateway.graphql.dto.*` — Jackson records mirroring the
  upstream JSON shapes; the BFF does NOT reuse backend DTOs.
- `com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar` —
  registers `productByIdLoader` against Spring's `BatchLoaderRegistry`.
- `com.ecommerce.gateway.graphql.controller.*` — `@QueryMapping` and
  `@SchemaMapping` resolvers, plus a `SchemaAdapterController` that
  bridges GraphQL field names to the BFF DTO shape.
- `com.ecommerce.gateway.graphql.config.GraphQlSecurityConfig` —
  `@EnableReactiveMethodSecurity` so `@PreAuthorize` works.
- Tests (8 classes, 39 tests):
  - `ProductGraphQlControllerTest` — slice tests for product / products /
    recommendations.
  - `CartGraphQlControllerTest` — auth-required + nested resolver path.
  - `OrderGraphQlControllerTest` — auth-required + paged orders.
  - `RecommendationDataLoaderTest` — proves 5 OrderItem product lookups
    collapse to a single `findByIds` call.
  - `SchemaValidationIntegrationTest` — full `@SpringBootTest` proving
    the schema is parsed and every Query field has a resolver.
  - `ProductBackendClientTest` + `BackendClientsTest` — MockWebServer
    drives WebClient against URI templates, headers, error mapping.
  - `DtoHelpersTest` + `SchemaAdapterControllerTest` — exhaustive null
    handling.

## Coverage

| Metric         | graphql package |
|----------------|------------------|
| Instructions   | 93.4 %           |
| Lines          | 93.1 %           |
| Branches       | 73.4 %           |

Both the Maven smoke commands pass:

```
mvn -pl infrastructure/api-gateway compile -DskipTests   # BUILD SUCCESS
mvn -pl infrastructure/api-gateway test                   # 39/39 green
```

## Decisions and trade-offs

- **Inside the gateway, not a separate service.** Reuses Eureka, JWT,
  no new pod. Documented rationale in `GRAPHQL_BFF.md`.
- **No Apollo Federation.** Single graph, single team — federation is
  premature.
- **No bulk endpoint on product-service yet.** The DataLoader's
  `findByIds` fans out per id with bounded concurrency; when the
  upstream adds `GET /api/products?ids=…` we change one method.
- **`liveStockQty` returns the `stockQuantity` snapshot.** Real-time
  via SSE is a follow-up — the schema already marks it nullable so the
  upgrade is non-breaking.

## Open follow-ups

- Subscriptions for live cart / live inventory.
- Bind `cart(userId:$x)` to JWT subject in the resolver (currently
  delegated to cart-service).
- Persisted queries + complexity limits before exposing publicly.
- Re-evaluate Federation when a second BFF or a vendor portal lands.

## Files touched (top-level)

- `infrastructure/api-gateway/pom.xml`
- `infrastructure/api-gateway/src/main/resources/application.yml`
- `infrastructure/api-gateway/src/main/resources/graphql/schema.graphqls`
- `infrastructure/api-gateway/src/main/java/com/ecommerce/gateway/config/SecurityConfig.java`
- `infrastructure/api-gateway/src/main/java/com/ecommerce/gateway/graphql/...` (new)
- `infrastructure/api-gateway/src/test/java/com/ecommerce/gateway/graphql/...` (new)
- `infrastructure/api-gateway/src/test/resources/application-test.yml` (new)
- `docs/GRAPHQL_BFF.md` (new)
- `docs/SCALING_AND_IMPROVEMENTS.md` (applied stamp)
- `docs/API_DOCUMENTATION.md` (cross-reference)
- `todo.md`
