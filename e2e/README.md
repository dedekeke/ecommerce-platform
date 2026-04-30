# E2E Tests — Playwright

## Prerequisites

All five frontend applications and the API gateway must be running before executing the test suite.

| Service | Command | URL |
|---------|---------|-----|
| shell-app | `cd frontend/shell-app && npm run dev` | http://localhost:5173 |
| product-catalog-mfe | `cd frontend/product-catalog-mfe && npm run dev` | http://localhost:5001 |
| cart-mfe | `cd frontend/cart-mfe && npm run dev` | http://localhost:5002 |
| checkout-mfe | `cd frontend/checkout-mfe && npm run dev` | http://localhost:5003 |
| user-dashboard-mfe | `cd frontend/user-dashboard-mfe && npm start` | http://localhost:5004 |
| API gateway | see docker-compose.yml | http://localhost:8080 |

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

## Notes

- The `webServer` option is intentionally absent from `playwright.config.ts`. Start the stack manually before running tests.
- Auth0 is in use. The auth-gate tests rely on the in-app `RoleGuard` rendering a `403` page synchronously — no live Auth0 credentials are required.
- The golden-path full checkout test (`full checkout flow fills address...`) requires the checkout-mfe and order service to be running. If the order service is down, this test will be skipped or fail gracefully at the `place order` step.
