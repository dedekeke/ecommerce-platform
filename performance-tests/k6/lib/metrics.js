// Shared custom metrics. Declared once and imported everywhere so all
// scenarios feed the same trends/rates that the thresholds in config.js key
// off of.

import { Trend, Rate, Counter } from 'k6/metrics';

// Latency of read/browse requests (catalog list, product detail).
export const browseLatency = new Trend('browse_latency', true);

// Latency of each checkout step (add-to-cart, create-order, payment-intent).
export const checkoutLatency = new Trend('checkout_latency', true);

// Business error rate: incremented on any request that did not meet its
// success check. Distinct from k6's transport-level http_req_failed.
export const errors = new Rate('errors');

// Absolute counts, handy for at-a-glance run summaries.
export const browseRequests = new Counter('browse_requests');
export const checkoutRequests = new Counter('checkout_requests');
