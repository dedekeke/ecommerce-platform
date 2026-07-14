// Golden-path load scenario through the api-gateway.
//
//   browse catalog -> product detail  (always, public)
//        -> add to cart -> create order -> payment intent  (only when AUTH_TOKEN set)
//
// Profiles (PROFILE env): smoke | load | stress. See lib/config.js.
// Run examples are documented in performance-tests/k6/README.md.

import { group, sleep } from 'k6';
import { config, buildScenarios, thresholds } from '../lib/config.js';
import { hasToken } from '../lib/auth.js';
import {
  browseProducts,
  getProductDetail,
  addToCart,
  createOrder,
  createPaymentIntent,
} from '../lib/checkout.js';

export const options = {
  scenarios: buildScenarios(),
  thresholds,
  // Surface a compact tag breakdown in the end-of-run summary.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// One-time banner so run logs make the effective config obvious.
export function setup() {
  const mode = hasToken() ? 'AUTHENTICATED (full checkout)' : 'BROWSE-ONLY (no AUTH_TOKEN)';
  console.log(
    `k6 golden-path | profile=${config.profile} target=${config.targetVus}vus ` +
      `duration=${config.duration} baseUrl=${config.baseUrl} versioned=${config.useVersioned} | ${mode}`
  );
  return { authenticated: hasToken() };
}

export default function (data) {
  const authenticated = data.authenticated;
  let productId;

  group('browse', function () {
    const ids = browseProducts(authenticated);
    productId = pickProductId(ids);
    if (productId !== null) {
      getProductDetail(productId, authenticated);
    }
  });

  // Checkout requires a token. Without one we stay a pure read-load generator.
  if (authenticated) {
    group('checkout', function () {
      const cartProduct = productId !== null ? productId : 1;
      addToCart(cartProduct, 1);
      const order = createOrder();
      if (order && order.id) {
        const amount = order.totalAmount || 10.0;
        createPaymentIntent(order.id, amount);
      }
    });
  }

  sleep(config.thinkTime);
}

// Pick a pseudo-random product from the page so detail requests spread across
// the catalog rather than hammering a single hot row. Returns null on empty.
function pickProductId(ids) {
  if (!ids || ids.length === 0) {
    return null;
  }
  return ids[Math.floor(Math.random() * ids.length)];
}
