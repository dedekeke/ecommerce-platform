// Flow helpers that map 1:1 onto the gateway routes exercised by the golden
// path. Each helper records latency into the shared trends and flags business
// errors, returning the parsed payload (or null) so callers can chain steps.
//
// Route notes (verified against infrastructure/api-gateway application.yml and
// SecurityConfig):
//   - GET /api/products (+/{id})   : public, unversioned. The versioned
//                                     /api/v1/products GET is NOT permitAll, so
//                                     browse-only unauthenticated runs MUST use
//                                     the unversioned path.
//   - POST /api/[v1/]cart/items     : authenticated; owner = token subject.
//   - POST /api/[v1/]orders         : authenticated; userId read from body.
//   - POST /api/[v1/]payments/intents: authenticated; owner = token subject.

import http from 'k6/http';
import { check } from 'k6';
import { config } from './config.js';
import { authHeaders, jsonHeaders, resolveUserId } from './auth.js';
import {
  browseLatency,
  checkoutLatency,
  errors,
  browseRequests,
  checkoutRequests,
} from './metrics.js';

// Browse GET path: unversioned is public; v1 requires auth. Use v1 only when
// authenticated AND versioned routing is enabled.
function productsBase(authenticated) {
  const versioned = config.useVersioned && authenticated;
  return `${config.baseUrl}${versioned ? '/api/v1' : '/api'}/products`;
}

// Authenticated resource base (cart/orders/payments) honours the version flag.
function apiBase() {
  return `${config.baseUrl}${config.useVersioned ? '/api/v1' : '/api'}`;
}

function record(res, trend, ok, requestCounter) {
  trend.add(res.timings.duration);
  errors.add(!ok);
  requestCounter.add(1);
}

// GET catalog page -> returns list of product ids (may be empty).
export function browseProducts(authenticated) {
  const res = http.get(`${productsBase(authenticated)}?page=0&size=20`, {
    headers: jsonHeaders(),
    tags: { name: 'browse_products' },
  });
  const ok = check(res, {
    'products: status 200': (r) => r.status === 200,
    'products: has body': (r) => !!r.body && r.body.length > 0,
  });
  record(res, browseLatency, ok, browseRequests);
  return extractProductIds(res);
}

// GET a single product detail page.
export function getProductDetail(productId, authenticated) {
  const res = http.get(`${productsBase(authenticated)}/${productId}`, {
    headers: jsonHeaders(),
    tags: { name: 'product_detail' },
  });
  const ok = check(res, {
    'product detail: status 200': (r) => r.status === 200,
  });
  record(res, browseLatency, ok, browseRequests);
  return safeJson(res);
}

// POST add a product to the authenticated user's cart.
export function addToCart(productId, quantity) {
  const res = http.post(
    `${apiBase()}/cart/items`,
    JSON.stringify({ productId: String(productId), quantity: quantity || 1 }),
    { headers: authHeaders(), tags: { name: 'add_to_cart' } }
  );
  const ok = check(res, {
    'add to cart: status 200': (r) => r.status === 200,
  });
  record(res, checkoutLatency, ok, checkoutRequests);
  return safeJson(res);
}

// POST create an order for the current user.
export function createOrder() {
  const body = {
    userId: resolveUserId(),
    userEmail: 'k6-loadtest@example.com',
    userName: 'k6 Load Test',
    shippingAddress: {
      street: '1 Load Test Way',
      city: 'Testville',
      state: 'CA',
      postalCode: '90001',
      country: 'US',
    },
  };
  const res = http.post(`${apiBase()}/orders`, JSON.stringify(body), {
    headers: authHeaders(),
    tags: { name: 'create_order' },
  });
  const ok = check(res, {
    'create order: status 201': (r) => r.status === 201,
    'create order: returns id': (r) => !!(safeJson(r) || {}).id,
  });
  record(res, checkoutLatency, ok, checkoutRequests);
  return safeJson(res);
}

// POST create a Stripe payment intent for the given order.
export function createPaymentIntent(orderId, amount) {
  const body = {
    orderId: String(orderId),
    amount: amount || 10.0,
    currency: 'usd',
  };
  const res = http.post(`${apiBase()}/payments/intents`, JSON.stringify(body), {
    headers: authHeaders(),
    tags: { name: 'payment_intent' },
  });
  const ok = check(res, {
    'payment intent: status 201': (r) => r.status === 201,
    'payment intent: has clientSecret': (r) => !!(safeJson(r) || {}).clientSecret,
  });
  record(res, checkoutLatency, ok, checkoutRequests);
  return safeJson(res);
}

// --- parsing helpers ------------------------------------------------------

function safeJson(res) {
  try {
    return res.json();
  } catch (_e) {
    return null;
  }
}

// The catalog endpoint returns a Spring Data Page: { content: [ { id, ... } ] }.
function extractProductIds(res) {
  const body = safeJson(res);
  if (!body || !Array.isArray(body.content)) {
    return [];
  }
  return body.content.map((p) => p.id).filter((id) => id !== undefined && id !== null);
}
