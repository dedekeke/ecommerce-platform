// Authentication helpers.
//
// The gateway is a JWT resource server. We do NOT perform an Auth0
// client-credentials handshake here because those credentials are not
// available in every environment (and must never be committed). Instead a
// pre-minted bearer token is injected via the AUTH_TOKEN env var. When absent
// the suite degrades to a browse-only, unauthenticated run.

import encoding from 'k6/encoding';

const token = (__ENV.AUTH_TOKEN || '').trim();

export function hasToken() {
  return token.length > 0;
}

// Headers for authenticated requests. Callers should only use these when
// hasToken() is true.
export function authHeaders() {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
}

export function jsonHeaders() {
  return { 'Content-Type': 'application/json', Accept: 'application/json' };
}

// Resolve the user id the checkout flow should act as. The payment-intent
// endpoint derives the owner from the token subject, and the order endpoint
// reads userId from the body, so we keep them consistent by decoding `sub`
// from the JWT. Falls back to USER_ID env, then a deterministic default.
export function resolveUserId() {
  const fromToken = subjectFromToken();
  return fromToken || __ENV.USER_ID || 'k6-load-test-user';
}

function subjectFromToken() {
  if (!hasToken()) {
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
