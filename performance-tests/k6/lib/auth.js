// Authentication helpers.
//
// The gateway is a JWT resource server. We do NOT perform an Auth0
// client-credentials handshake here because those credentials are not
// available in every environment (and must never be committed). Pre-minted
// bearer tokens are injected via env. When none are present the suite degrades
// to a browse-only, unauthenticated run.
//
// WHY A TOKEN POOL: the gateway's @Primary combinedKeyResolver rate-limits by
// principal name (the JWT `sub`), replenishRate 100 / burst 200, with no
// per-route override. A single shared token means every authenticated VU is
// ONE principal — so load/stress would measure the rate-limiter and contend on
// one shared cart row, not real capacity. Supply many tokens (distinct
// subjects) via AUTH_TOKENS / AUTH_TOKENS_FILE and they are assigned per-VU
// round-robin. A single AUTH_TOKEN still works but emits a loud warning.

import encoding from 'k6/encoding';
import { SharedArray } from 'k6/data';

// Loaded once in init context, shared read-only across all VUs.
const tokens = new SharedArray('auth_tokens', loadTokens);

function loadTokens() {
  // Priority: explicit list > file > single token.
  const list = (__ENV.AUTH_TOKENS || '').trim();
  if (list) {
    return splitTokens(list);
  }
  const file = (__ENV.AUTH_TOKENS_FILE || '').trim();
  if (file) {
    // open() is only valid in init context — SharedArray's callback qualifies.
    return splitTokens(open(file));
  }
  const single = (__ENV.AUTH_TOKEN || '').trim();
  return single ? [single] : [];
}

function splitTokens(raw) {
  return raw
    .split(/[\s,]+/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

export function hasToken() {
  return tokens.length > 0;
}

export function tokenCount() {
  return tokens.length;
}

// True when authenticated runs would be distorted by a single principal.
export function isSingleTokenPool() {
  return tokens.length === 1;
}

// Per-VU token, assigned round-robin so distinct VUs map to distinct principals
// (as long as the pool is large enough).
function currentToken() {
  if (tokens.length === 0) {
    return '';
  }
  const idx = (Math.max(__VU, 1) - 1) % tokens.length;
  return tokens[idx];
}

// Headers for authenticated requests. Only use when hasToken() is true.
export function authHeaders() {
  return {
    Authorization: `Bearer ${currentToken()}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
}

export function jsonHeaders() {
  return { 'Content-Type': 'application/json', Accept: 'application/json' };
}

// Resolve the user id the checkout flow acts as. The payment-intent endpoint
// derives the owner from the token subject and the order endpoint reads userId
// from the body, so we keep them consistent by decoding `sub` from this VU's
// token. Falls back to a per-VU synthetic id so orders stay distinguishable.
export function resolveUserId() {
  const fromToken = subjectFromToken(currentToken());
  if (fromToken) {
    return fromToken;
  }
  const base = __ENV.USER_ID || 'k6-load-test-user';
  return `${base}-vu${Math.max(__VU, 1)}`;
}

function subjectFromToken(token) {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null; // not a JWT (opaque token) — nothing to decode.
  }
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1]));
    return payload.sub || null;
  } catch (_e) {
    return null;
  }
}

function base64UrlDecode(segment) {
  let s = segment.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4 !== 0) {
    s += '=';
  }
  return encoding.b64decode(s, 'std', 's');
}
