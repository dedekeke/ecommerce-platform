# Real-time Inventory Sync (SSE)

> Back to [README](../README.md).

Browsers stream inventory stock-level changes from `inventory-service` via Server-Sent Events. This preserves the "in stock when you added it" UX guarantee in the cart and shows live "only N left" badges in the catalog.

## Why SSE, not WebSocket
- One-way (server → client) — matches the use case.
- Plain HTTP/1.1 — proxies, CORS, and Auth0 just work.
- Browser-native auto-reconnect via `EventSource`.
- Switch to WebSocket (STOMP over Spring Messaging) when concurrent connections exceed ~5k per pod, or when bidirectional channels become useful (chat, presence, collaborative cart).

## Endpoint
```
GET /api/inventory/stream
GET /api/inventory/stream?productIds=PROD-1,PROD-2   # optional filter
```
Headers: `Cache-Control: no-cache`, `X-Accel-Buffering: no`, `Connection: keep-alive`. The gateway forwards these untouched.

## Event shape
Event name: `stock-update`. Payload:
```json
{
  "productId": "PROD-XYZ",
  "sku": "SKU-XYZ",
  "availableQty": 14,
  "previousQty": 17,
  "ts": "2026-04-30T12:34:56"
}
```
Heartbeats are sent as SSE comments every `inventory.sse.heartbeat-interval-ms` (default 15 s).

## Backend flow
`InventoryService.adjustStock` / `restoreStock` / etc. fire a Spring `ApplicationEventPublisher` event. `InventorySseController.@EventListener onStockChanged` hands the event to `InventorySseRegistry`, which fans out to every open `SseEmitter` whose filter matches.

## Frontend
- Shell-app exposes `useInventoryStream()` (registered in `App.tsx`) — opens the connection once and writes to a Zustand store (`inventoryStore`) keyed by productId.
- Product Catalog MFE's `ProductCard` reads from the same store key (federated zustand singleton, same pattern as `cart-storage`) and renders a low-stock badge ("Only N left") below 5 units, "Out of Stock" at zero.
- Reconnect is exponential backoff up to 30 s.

## Curl probe
```bash
curl -N http://localhost:8080/api/inventory/stream
# heartbeats every 15s; stock-update events when inventory changes
```

## Scaling notes
- Each open emitter parks on a virtual thread — JDK 21 makes this nearly free up to ~10k concurrent connections per pod.
- Past ~5k, switch the broadcast layer to Redis pub-sub or migrate the transport to WebSocket. KEDA already scales the service on Kafka lag — for SSE concurrent-connection scaling, add a custom HPA metric (`inventory_sse_connections_active`) once the count gauge is exposed.
- `InventorySseRegistry.broadcast` evicts emitters whose `send` throws — clients reconnect via `EventSource`'s native retry.
