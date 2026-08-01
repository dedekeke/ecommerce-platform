# GraalVM Native Image — API Gateway (proof-of-concept)

> Status: configured but not yet built in CI (build is slow). Activated by Maven profile `native`.

## Why

The api-gateway is a small, mostly stateless reactive process (Spring Cloud Gateway + GraphQL BFF). It is a good first candidate for AOT compilation because:

1. **Cold-start matters** — gateways are scaled most aggressively under traffic spikes, so JVM warmup is a tax on every new pod.
2. **Memory matters** — at 11 microservices the JVM-per-pod overhead is significant in dev/test clusters.
3. **Surface area is small** — fewer reflection-heavy beans than e.g. order-service with JPA + saga state machines.

Targets (rough industry numbers, validate before relying on them):

| Metric | JVM (current) | Native (target) |
|---|---|---|
| Image size | ~340 MB | < 100 MB |
| Cold start to ready | 8–12 s | < 1 s |
| RSS at idle | ~450 MB | ~120 MB |
| Throughput (steady state) | baseline | ~10–20 % lower |

## How to build

Native build (requires GraalVM 21+ and the `native-image` binary on PATH):

```bash
mvn -Pnative -pl infrastructure/api-gateway -am -DskipTests native:compile
```

Output: `infrastructure/api-gateway/target/api-gateway` (executable).

Docker (uses `ghcr.io/graalvm/native-image-community:21` so you don't need GraalVM locally):

```bash
docker build -f infrastructure/api-gateway/Dockerfile.native -t api-gateway:native .
```

The native build takes ~5–10 minutes vs ~30 s for the JVM build, so we do **not** run it on every push. It runs in a dedicated weekly CI job.

## Reflection / serialization hints

Spring Boot's AOT processor can statically discover most beans, but a few things still need explicit hints:

- **Jackson DTOs** (records used for BFF responses) — registered via `GatewayRuntimeHints` with `MemberCategory.INVOKE_DECLARED_CONSTRUCTORS` and `INVOKE_DECLARED_METHODS`.
- **GraphQL schema files** under `classpath:graphql/*.graphqls` — registered as resource patterns.
- **Spring Cloud Gateway routes** — declared programmatically via `RouteLocator` so they are discovered by AOT without extra hints.

When adding a new BFF DTO, add it to the `REFLECTIVE_DTOS` array in `GatewayRuntimeHints.java`. When adding a new GraphQL `*.graphqls` file under `src/main/resources/graphql/`, no change is needed because the resource pattern is already covered.

## Trade-offs

- **Build time** — native-image is single-threaded for the analysis phase. Expect 5–10 minutes per build; offload to CI.
- **No JIT** — peak throughput is lower than a warmed-up HotSpot. Native is best for short-lived or cold-start-sensitive workloads, not throughput-bound batch.
- **No standard JFR / Java agents** — observability tooling that relies on `-javaagent` (e.g. some APMs) won't attach. Use OpenTelemetry SDK (already in use here) instead.
- **Profile-guided optimisation (PGO)** — GraalVM Enterprise supports PGO for ~15 % more throughput; community edition does not. If we adopt native in production, evaluating GraalVM EE is on the follow-up list.
- **Reachability metadata drift** — if Spring or Resilience4j ship a release that introduces a new reflective code path, the build may fail at native-image time. Pin and test before bumping.

## Follow-ups

- [ ] Add a weekly CI job that runs `mvn -Pnative native:compile` and pushes to a separate registry tag.
- [ ] Compare actual image size, startup time, and p95 latency on a smoke test once the first build runs.
- [ ] Evaluate GraalVM EE for PGO if native goes to production.
- [ ] Repeat the exercise for `notification-service` next (also a good candidate: small, event-driven).
