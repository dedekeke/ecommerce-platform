import { test, expect } from '@playwright/test'

/**
 * Auth gate tests: /admin/* shows 403 for unauthenticated / non-admin users.
 *
 * Auth0 redirects make a true unauthenticated flow hard to automate without
 * mocking.  These tests verify the in-app role-guard behaviour using the
 * RoleGuard component (data-testid="forbidden-page") which is rendered when
 * the user is authenticated but lacks the 'admin' role, or when the JWT
 * contains no roles claim at all.
 *
 * The 403 page is rendered synchronously once the RoleGuard evaluates — no
 * backend round-trip is needed so the tests work against a running shell-app
 * without live Auth0 credentials.
 *
 * Under mock auth mode (VITE_AUTH_MODE=mock) the visitor is authenticated as
 * "e2e|test-user" with no roles claim, so the same 403 page renders. Both
 * branches of the .or() locator then resolve (testid + heading), hence the
 * .first() to satisfy strict mode.
 */

test.describe('Admin route auth gate', () => {
  test('unauthenticated visit to /admin shows 403 forbidden page', async ({ page }) => {
    await page.goto('/admin')
    await expect(
      page.getByTestId('forbidden-page').or(
        page.getByRole('heading', { name: /403/i })
      ).first()
    ).toBeVisible({ timeout: 10000 })
  })

  test('/admin/products shows 403 for unauthenticated user', async ({ page }) => {
    await page.goto('/admin/products')
    await expect(
      page.getByTestId('forbidden-page').or(
        page.getByRole('heading', { name: /403/i })
      ).first()
    ).toBeVisible({ timeout: 10000 })
  })

  test('/admin/orders shows 403 for unauthenticated user', async ({ page }) => {
    await page.goto('/admin/orders')
    await expect(
      page.getByTestId('forbidden-page').or(
        page.getByRole('heading', { name: /403/i })
      ).first()
    ).toBeVisible({ timeout: 10000 })
  })

  test('403 page contains a go-home link', async ({ page }) => {
    await page.goto('/admin')
    const heading = page.getByTestId('forbidden-page').or(
      page.getByRole('heading', { name: /403/i })
    ).first()
    await expect(heading).toBeVisible({ timeout: 10000 })

    await expect(page.getByRole('link', { name: /go home/i })).toBeVisible()
  })

  test('clicking go-home on 403 page navigates to /', async ({ page }) => {
    await page.goto('/admin')
    await expect(
      page.getByTestId('forbidden-page').or(
        page.getByRole('heading', { name: /403/i })
      ).first()
    ).toBeVisible({ timeout: 10000 })

    await page.getByRole('link', { name: /go home/i }).click()
    await expect(page).toHaveURL('/', { timeout: 8000 })
  })
})
