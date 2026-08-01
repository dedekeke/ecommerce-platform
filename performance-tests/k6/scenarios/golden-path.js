// Golden-path load scenario through the api-gateway.
//
//   browse catalog -> product detail  (always, public)
//        -> add to cart -> create order -> payment intent  (only when a token is set)
//
// Profiles (PROFILE env): smoke | load | stress. See lib/config.js.
// Run examples are documented in performance-tests/k6/README.md.

import { group, sleep } from 'k6';
import { config, buildScenarios, thresholds } from '../lib/config.js';
import { hasToken, tokenCount, isSingleTokenPool } from '../lib/auth.js';
import {
  browseProducts,
  getProductDetail,
  addToCart,
  createOrder,
  createPaymentIntent,
} from '../lib/checkout.js';

// Fallback product id used only when the catalog page comes back empty.
const FALLBACK_PRODUCT_ID = __ENV.FALLBACK_PRODUCT_ID || '1';
const FALLBACK_AMOUNT = 10.0;
// Short pause between checkout steps to model client/server round-trip pacing.
const STEP_PAUSE = parseFloat(__ENV.STEP_PAUSE || '0.3');

export const options = {
  scenarios: buildScenarios(),
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Warn at most once per VU instance to avoid flooding logs at high VU counts.
const warned = {};
function warnOnce(key, message) {
  if (!warned[key]) {
    warned[key] = true;
    console.warn(message);
  }
}

export function setup() {
  const authed = hasToken();
  const mode = authed ? 'AUTHENTICATED (full checkout)' : 'BROWSE-ONLY (no token set)';
  console.log(
    `k6 golden-path | profile=${config.profile} target=${config.targetVus}vus ` +
      `duration=${config.duration} baseUrl=${config.baseUrl} versioned=${config.useVersioned} | ${mode}`
  );

  if (authed) {
    console.log(`Auth token pool size: ${tokenCount()}`);
    // The gateway rate-limits by JWT subject (combinedKeyResolver). One token
    // for many VUs collapses to a single principal + a single shared cart row.
    if (isSingleTokenPool() && config.profile !== 'smoke') {
      console.warn(
        '################################################################\n' +
          '# WARNING: only ONE auth token for a load/stress run.          #\n' +
          '# The gateway rate-limits per JWT subject (100/s, burst 200)   #\n' +
          '# and all VUs share ONE cart. These numbers measure the        #\n' +
          '# throttle, NOT capacity, and are INVALID for capacity         #\n' +
          '# planning. Provide many tokens via AUTH_TOKENS / *_FILE.      #\n' +
          '################################################################'
      );
    }
    if (tokenCount() > 0 && tokenCount() < config.targetVus) {
      console.warn(
        `NOTE: token pool (${tokenCount()}) < target VUs (${config.targetVus}); ` +
          'VUs will share subjects and hit the per-principal rate limit sooner.'
      );
    }
  }

  return { authenticated: authed };
}

export default function (data) {
  const authenticated = data.authenticated;
  let productId = null;

  group('browse', function () {
    const ids = browseProducts(authenticated);
    productId = pickProductId(ids);
    if (productId !== null) {
      getProductDetail(productId, authenticated);
    } else {
      warnOnce('empty_catalog', 'Catalog page returned no products; skipping detail fetch.');
    }
  });

  // Checkout requires a token. Without one we stay a pure read-load generator.
  if (authenticated) {
    group('checkout', function () {
      let cartProduct = productId;
      if (cartProduct === null) {
        cartProduct = FALLBACK_PRODUCT_ID;
        warnOnce(
          'fallback_product',
          `No product id from catalog; using FALLBACK_PRODUCT_ID=${FALLBACK_PRODUCT_ID} for cart.`
        );
      }

      addToCart(cartProduct, 1);
      sleep(STEP_PAUSE);

      const order = createOrder();
      if (order && order.id) {
        sleep(STEP_PAUSE);
        const amount = resolveAmount(order);
        createPaymentIntent(order.id, amount);
      }
    });
  }

  sleep(config.thinkTime);
}

// Payment amount must come from the real order response. The Order entity's
// field is `total`; fall back only if the backend omits/malforms it.
function resolveAmount(order) {
  const total = Number(order.total);
  if (Number.isFinite(total) && total > 0) {
    return total;
  }
  warnOnce(
    'missing_total',
    `Order response missing a valid 'total' (got ${JSON.stringify(order.total)}); ` +
      `falling back to ${FALLBACK_AMOUNT}.`
  );
  return FALLBACK_AMOUNT;
}

// Pick a pseudo-random product from the page so detail requests spread across
// the catalog rather than hammering a single hot row. Returns null on empty.
function pickProductId(ids) {
  if (!ids || ids.length === 0) {
    return null;
  }
  return ids[Math.floor(Math.random() * ids.length)];
}
