# Frontend Coverage Report

Generated: 2026-04-29

Coverage threshold: **≥80% statements** for all packages.

---

## React MFEs (Vitest + @vitest/coverage-v8)

| Package | Statements | Branches | Functions | Lines | Status |
|---------|-----------|----------|-----------|-------|--------|
| shell-app | 89.45% | 82.62% | 81.45% | 92.30% | PASS |
| product-catalog-mfe | 100.00% | 85.29% | 100.00% | 100.00% | PASS |
| cart-mfe | 95.76% | 82.92% | 92.45% | 98.05% | PASS |
| checkout-mfe | 97.10% | 87.80% | 96.51% | 98.52% | PASS |

### shell-app — Files Lifted

`shell-app` started at **82.1%** statements.  The following files were below the 80% floor and were lifted by adding targeted tests:

| File | Before | After | Tests Added |
|------|--------|-------|------------|
| `src/hooks/useNotifications.ts` | 5% | 100% | `useNotifications.test.ts` — covers all 5 show helpers, `show()`, `remove()`, `clear()`, unique ID generation |
| `src/api/apiClient.ts` | 50% | 88.88% | `apiClient.interceptors.test.ts` — auth interceptor Bearer token attachment, skipAuth, null token, rejected token; response interceptor 401/403 callbacks, 500 passthrough, no-callback guard |
| `src/App.tsx` | 53.84% | ~90% | `App.test.tsx` — route rendering for all 8 routes, Auth0 loading skeleton, authenticated admin guard |

---

## Angular MFEs (Karma + ChromeHeadless)

| Package | Statements | Branches | Functions | Lines | Status |
|---------|-----------|----------|-----------|-------|--------|
| user-dashboard-mfe | 80.78% | 48.14% | 62.19% | 81.95% | PASS |
| admin-dashboard-mfe | 84.12% | 57.77% | 71.29% | 85.50% | PASS |

### user-dashboard-mfe — Files Lifted

`user-dashboard-mfe` started at **75.98%** statements.  The following were lifted:

| File | Tests Added |
|------|------------|
| `auth.interceptor.ts` | `auth.interceptor.spec.ts` — 4 cases: no token function, null token, valid token attaches Authorization header, original request not mutated |
| `order-history.page.ts` | `order-history.navigation.spec.ts` — `onViewOrder` navigation, `onStatusChange` reset, `onPageChange`, empty state, error state stops loading |
| `dashboard-overview.page.ts` | `dashboard-overview.navigation.spec.ts` — `onViewOrder` href, orders displayed when returned, loading false after fetch, greeting with name |

### Notes on Pre-Existing Failures

`user-dashboard-mfe` has **3 pre-existing test failures** in `AddressFormComponent` and `ProfileFormComponent` (not introduced by this work):

- `AddressFormComponent — should emit formSubmit with valid form data on save`
- `ProfileFormComponent — should emit formSubmit with updated values on save`
- one additional form component spec failure

These failures exist on the base branch and are out of scope for this coverage task.  They are tracked as a follow-up.  They do not affect the statement coverage count (the instrumented lines are still exercised by other tests).

### Note on Branch Coverage

Angular's Karma-based branch coverage is lower than statements across both MFEs because template control-flow blocks (`@if`, `@for`) generate additional branches that are only measurable when the component renders in every conditional state.  The `48%` branch figure for `user-dashboard-mfe` reflects uncovered `@else` blocks in templates (loading skeletons, error states) that would require additional component-fixture tests.  These are documented here for follow-up but are not blocking given the statement threshold is met.

---

## How to Reproduce

### React MFEs

```bash
cd frontend/shell-app && npm run test:coverage
cd frontend/product-catalog-mfe && npm run test:coverage
cd frontend/cart-mfe && npm run test:coverage
cd frontend/checkout-mfe && npm run test:coverage
```

HTML reports are written to each package's `coverage/` directory.

### Angular MFEs

```bash
cd frontend/user-dashboard-mfe && npx ng test --code-coverage --watch=false --browsers=ChromeHeadless
cd frontend/admin-dashboard-mfe && npx ng test --code-coverage --watch=false --browsers=ChromeHeadless
```

HTML reports are written to `coverage/<package-name>/index.html`.
