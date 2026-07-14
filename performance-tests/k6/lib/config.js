// Central runtime configuration for the k6 load suite.
//
// Everything is driven by environment variables so the same scripts run
// unchanged in CI, locally, or against a staging gateway. Nothing is
// hard-coded and no secrets live in the repo — the bearer token is only ever
// read from the AUTH_TOKEN env var (see lib/auth.js).

import { fail } from 'k6';

// --- Profiles -------------------------------------------------------------
// smoke  : CI-friendly sanity check (few VUs, short).
// load   : realistic sustained traffic, ramp to a few hundred VUs.
// stress : find the breaking point, ramp toward a thousand VUs.
const PROFILES = {
  smoke: { vus: 5, target: 5, duration: '1m', ramping: false },
  load: { vus: 20, target: 200, duration: '9m', ramping: true },
  stress: { vus: 50, target: 1000, duration: '23m', ramping: true },
};

function intEnv(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const parsed = parseInt(raw, 10);
  if (Number.isNaN(parsed) || parsed <= 0) {
    fail(`Invalid ${name}="${raw}" — expected a positive integer`);
  }
  return parsed;
}

const PROFILE = (__ENV.PROFILE || 'smoke').toLowerCase();
if (!PROFILES[PROFILE]) {
  fail(`Unknown PROFILE="${PROFILE}" — expected one of: ${Object.keys(PROFILES).join(', ')}`);
}

const profile = PROFILES[PROFILE];

// Optional overrides. VUS overrides the ramp target (ramping profiles) or the
// constant VU count (smoke). DURATION overrides the total scenario duration.
const targetVus = intEnv('VUS', profile.target);
const duration = __ENV.DURATION || profile.duration;

// API versioning: the gateway exposes both `/api/<resource>` (legacy,
// Deprecation-stamped) and `/api/v1/<resource>` (canonical). Default to v1.
// Set API_VERSION=none to exercise the legacy unversioned routes.
const useVersioned = (__ENV.API_VERSION || 'v1').toLowerCase() !== 'none';

export const config = {
  profile: PROFILE,
  baseUrl: (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, ''),
  targetVus,
  duration,
  useVersioned,
  // sleep between iterations to model think-time (seconds).
  thinkTime: parseFloat(__ENV.THINK_TIME || '1'),
};

// Build the k6 scenario executor block for the active profile.
export function buildScenarios() {
  if (!profile.ramping) {
    return {
      golden_path: {
        executor: 'constant-vus',
        vus: intEnv('VUS', profile.vus),
        duration,
      },
    };
  }

  // Ramp up -> hold at target -> ramp down. Durations are derived from the
  // total so a DURATION override still produces a sensible three-phase shape.
  return {
    golden_path: {
      executor: 'ramping-vus',
      startVUs: 0,
      gracefulRampDown: '30s',
      stages: [
        { duration: '2m', target: targetVus },
        { duration: durationMinus(duration, '4m'), target: targetVus },
        { duration: '2m', target: 0 },
      ],
    },
  };
}

// Subtract a fixed tail (ramp up + ramp down) from the total duration for the
// hold phase, flooring at 1m so short overrides still hold briefly.
function durationMinus(total, tail) {
  const totalSec = toSeconds(total);
  const tailSec = toSeconds(tail);
  const hold = Math.max(totalSec - tailSec, 60);
  return `${hold}s`;
}

function toSeconds(d) {
  const match = /^(\d+)(s|m|h)$/.exec(String(d).trim());
  if (!match) {
    fail(`Invalid duration "${d}" — use forms like 30s, 5m, 1h`);
  }
  const value = parseInt(match[1], 10);
  const unit = match[2];
  return unit === 'h' ? value * 3600 : unit === 'm' ? value * 60 : value;
}

// Thresholds are SLOs, not tuning knobs — kept constant across profiles so a
// passing smoke run means the same thing as a passing load run.
export const thresholds = {
  // Browse (catalog list + product detail) must stay snappy.
  browse_latency: ['p(95)<500'],
  // Full checkout chain (cart -> order -> payment intent) may be heavier.
  checkout_latency: ['p(95)<1500'],
  // Business error rate across all tracked requests.
  errors: ['rate<0.01'],
  // Guardrail on k6's own request-failure metric.
  http_req_failed: ['rate<0.01'],
};
