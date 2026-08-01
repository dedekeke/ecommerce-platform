# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth-gate.spec.ts >> Admin route auth gate >> /admin/products shows 403 for unauthenticated user
- Location: tests/auth-gate.spec.ts:27:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByTestId('forbidden-page').or(getByRole('heading', { name: /403/i }))
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByTestId('forbidden-page').or(getByRole('heading', { name: /403/i }))

```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test'
  2  | 
  3  | /**
  4  |  * Auth gate tests: /admin/* shows 403 for unauthenticated / non-admin users.
  5  |  *
  6  |  * Auth0 redirects make a true unauthenticated flow hard to automate without
  7  |  * mocking.  These tests verify the in-app role-guard behaviour using the
  8  |  * RoleGuard component (data-testid="forbidden-page") which is rendered when
  9  |  * the user is authenticated but lacks the 'admin' role, or when the JWT
  10 |  * contains no roles claim at all.
  11 |  *
  12 |  * The 403 page is rendered synchronously once the RoleGuard evaluates — no
  13 |  * backend round-trip is needed so the tests work against a running shell-app
  14 |  * without live Auth0 credentials.
  15 |  */
  16 | 
  17 | test.describe('Admin route auth gate', () => {
  18 |   test('unauthenticated visit to /admin shows 403 forbidden page', async ({ page }) => {
  19 |     await page.goto('/admin')
  20 |     await expect(
  21 |       page.getByTestId('forbidden-page').or(
  22 |         page.getByRole('heading', { name: /403/i })
  23 |       )
  24 |     ).toBeVisible({ timeout: 10000 })
  25 |   })
  26 | 
  27 |   test('/admin/products shows 403 for unauthenticated user', async ({ page }) => {
  28 |     await page.goto('/admin/products')
  29 |     await expect(
  30 |       page.getByTestId('forbidden-page').or(
  31 |         page.getByRole('heading', { name: /403/i })
  32 |       )
> 33 |     ).toBeVisible({ timeout: 10000 })
     |       ^ Error: expect(locator).toBeVisible() failed
  34 |   })
  35 | 
  36 |   test('/admin/orders shows 403 for unauthenticated user', async ({ page }) => {
  37 |     await page.goto('/admin/orders')
  38 |     await expect(
  39 |       page.getByTestId('forbidden-page').or(
  40 |         page.getByRole('heading', { name: /403/i })
  41 |       )
  42 |     ).toBeVisible({ timeout: 10000 })
  43 |   })
  44 | 
  45 |   test('403 page contains a go-home link', async ({ page }) => {
  46 |     await page.goto('/admin')
  47 |     const heading = page.getByTestId('forbidden-page').or(
  48 |       page.getByRole('heading', { name: /403/i })
  49 |     )
  50 |     await expect(heading).toBeVisible({ timeout: 10000 })
  51 | 
  52 |     await expect(page.getByRole('link', { name: /go home/i })).toBeVisible()
  53 |   })
  54 | 
  55 |   test('clicking go-home on 403 page navigates to /', async ({ page }) => {
  56 |     await page.goto('/admin')
  57 |     await expect(
  58 |       page.getByTestId('forbidden-page').or(
  59 |         page.getByRole('heading', { name: /403/i })
  60 |       )
  61 |     ).toBeVisible({ timeout: 10000 })
  62 | 
  63 |     await page.getByRole('link', { name: /go home/i }).click()
  64 |     await expect(page).toHaveURL('/', { timeout: 8000 })
  65 |   })
  66 | })
  67 | 
```