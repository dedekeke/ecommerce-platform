# E2E Tests — Playwright

## Prerequisites

All five frontend applications and the API gateway must be running before executing the test suite.

| Service | Command | URL |
|---------|---------|-----|
| shell-app | `cd frontend/shell-app && npm run dev:e2e` | http://localhost:5173 |
| product-catalog-mfe | `cd frontend/product-catalog-mfe && npm run build && npm run preview -- --port 5001 --strictPort` | http://localhost:5001 |
| cart-mfe | `cd frontend/cart-mfe && npm run build && npm run preview -- --port 5002 --strictPort` | http://localhost:5002 |
| checkout-mfe | `cd frontend/checkout-mfe && npm run build && npm run preview -- --port 5003 --strictPort` | http://localhost:5003 |
| user-dashboard-mfe | `cd frontend/user-dashboard-mfe && npm start` | http://localhost:5004 |
| API gateway | see docker-compose.yml | http://localhost:8080 |

> The webpack/vite-federation MFEs must run `build` + `preview` (not `dev`):
> @originjs/vite-plugin-federation only emits `assets/remoteEntry.js` during a
> build, so a plain `npm run dev` serves the index.html fallback and the shell
> fails with "Failed to fetch dynamically imported module".

Browsers must be installed once:

```bash
cd e2e
npm install
npx playwright install chromium firefox webkit
```

## Running Tests

```bash
cd e2e
npm test                  # headless across chromium, firefox, webkit
npm run test:headed       # headed (opens browser window)
npm run test:ui           # Playwright UI mode (interactive)
npm run report            # open last HTML report
```

Run a single file:

```bash
npx playwright test tests/a11y.spec.ts
npx playwright test tests/golden-path.spec.ts --project=chromium
```

## Test Files

| File | Description |
|------|-------------|
| `tests/golden-path.spec.ts` | Full purchase flow: product list → add to cart → checkout → order confirmation |
| `tests/cart-edge-cases.spec.ts` | Empty cart, quantity stepper, remove item, persistence across reload |
| `tests/auth-gate.spec.ts` | /admin/* returns 403 for unauthenticated / non-admin users |
| `tests/a11y.spec.ts` | axe-core WCAG 2.1 AA scan on every major route |

## Accessibility

The `a11y.spec.ts` tests use `@axe-core/playwright` to run automated WCAG 2.1 AA audits on each major route.

Intentionally allow-listed violations are documented inside `a11y.spec.ts` with the upstream issue link and the reason the finding is acceptable.

## Local Test Auth Mode (no Auth0 required)

The shell-app supports `VITE_AUTH_MODE=mock`, a **dev-server-only** auth mode built
for this suite. When active, the shell skips the Auth0 redirect entirely and treats
the visitor as authenticated with a deterministic identity:

| Field | Value |
|-------|-------|
| `sub` (`window.__getAuthUserId()`) | `e2e|test-user` |
| email | `e2e-test-user@example.com` |
| name | `E2E Test User` |
| token (`window.__getAuthToken()`) | `e2e-mock-token` (static dummy) |

This unblocks the auth-gated specs (golden-path, cart, a11y on protected routes)
in environments where the Auth0 tenant is unreachable. The mock user carries **no
roles claim**, so the `/admin` RoleGuard 403 behaviour tested in `auth-gate.spec.ts`
is unchanged.

How it is wired:

- `playwright.config.ts` declares a `webServer` entry that launches the shell with
  `npm run dev:e2e` (`VITE_AUTH_MODE=mock vite`) and **reuses** any server already
  on :5173 — so if you start the shell manually, use `npm run dev:e2e`, otherwise
  auth-gated specs will bounce to Auth0.
- MFE dev servers and backends are still started manually (table above).

Safety guards (mock auth can never reach production):

- **Build time**: `frontend/shell-app/mockAuthBuildGuard.ts` fails any `vite build`
  when `VITE_AUTH_MODE=mock` is set (grep marker: `MOCK_AUTH_PRODUCTION_GUARD`).
- **Runtime**: `src/auth/mockAuth.ts` only honours the flag when
  `import.meta.env.DEV` is true — built bundles have `DEV=false`.

### Backends: disable JWT validation locally

The mock token is a static dummy, not a signed JWT. The gateway and Spring services
validate real Auth0 JWTs by default, so for full-stack specs run the backends with
`SECURITY_ENABLED=false` (see each service's `application.yml` / root `.env`).
Do **not** set that flag in any deployed environment.

## Notes

- `playwright.config.ts` auto-starts only the shell-app (in mock auth mode). Start the MFEs and backends manually before running the full suite.
- Real Auth0 stays the default everywhere: without `VITE_AUTH_MODE=mock` the shell behaves exactly as before.
- The auth-gate tests rely on the in-app `RoleGuard` rendering a `403` page synchronously — no live Auth0 credentials are required.
- The golden-path full checkout test (`full checkout flow fills address...`) requires the checkout-mfe and order service to be running. If the order service is down, this test will be skipped or fail gracefully at the `place order` step.
