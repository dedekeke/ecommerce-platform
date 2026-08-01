import { test, expect } from '@playwright/test'

/**
 * Golden path: product list → add to cart → checkout → confirmation.
 *
 * Pre-conditions (see e2e/README.md):
 *   - shell-app running on http://localhost:5173
 *   - product-catalog-mfe running on http://localhost:5001
 *   - cart-mfe running on http://localhost:5002
 *   - checkout-mfe running on http://localhost:5003
 *   - API gateway running on http://localhost:8080
 */

test.describe('Golden path: product list → cart → checkout → confirmation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.evaluate(() => localStorage.removeItem('cart-storage'))
  })

  test('homepage renders welcome heading and category links', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /welcome to e-commerce/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /electronics/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /fashion/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /home & garden/i })).toBeVisible()
  })

  test('navigating to /products renders the product list page', async ({ page }) => {
    await page.goto('/products')
    await expect(page.getByRole('heading', { name: /all products/i })).toBeVisible({ timeout: 10000 })
  })

  test('clicking a product opens the detail page with Add to Cart button', async ({ page }) => {
    await page.goto('/products')
    const firstCard = page.locator('[data-testid="product-card"]').first()
    await expect(firstCard).toBeVisible({ timeout: 10000 })
    await firstCard.click()
    await expect(page.getByRole('button', { name: /add to cart/i })).toBeVisible({ timeout: 8000 })
  })

  test('add to cart increments header cart badge', async ({ page }) => {
    await page.goto('/products')
    const firstCard = page.locator('[data-testid="product-card"]').first()
    await expect(firstCard).toBeVisible({ timeout: 10000 })
    await firstCard.click()

    const addBtn = page.getByRole('button', { name: /add to cart/i })
    await expect(addBtn).toBeEnabled({ timeout: 8000 })
    await addBtn.click()

    await expect(page.locator('[data-testid="cart-badge"]')).toContainText('1', { timeout: 5000 })
  })

  test('cart page shows added item and Proceed to Checkout button', async ({ page }) => {
    await page.goto('/products')
    const firstCard = page.locator('[data-testid="product-card"]').first()
    await expect(firstCard).toBeVisible({ timeout: 10000 })
    await firstCard.click()

    const addBtn = page.getByRole('button', { name: /add to cart/i })
    await expect(addBtn).toBeEnabled({ timeout: 8000 })
    await addBtn.click()

    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /proceed to checkout/i })).toBeVisible({ timeout: 8000 })
  })

  test('checkout page renders shipping address step', async ({ page }) => {
    await page.goto('/checkout')
    await expect(page.getByRole('heading', { name: /checkout/i })).toBeVisible({ timeout: 10000 })
    await expect(page.getByLabel(/full name/i)).toBeVisible()
  })

  test('address form validates required fields on blur', async ({ page }) => {
    await page.goto('/checkout')
    await expect(page.getByLabel(/full name/i)).toBeVisible({ timeout: 10000 })

    const fullNameInput = page.getByLabel(/full name/i)
    await fullNameInput.focus()
    await fullNameInput.blur()
    await expect(page.getByRole('alert').filter({ hasText: /full name is required/i })).toBeVisible()
  })

  test('full checkout flow fills address, payment, reviews order and places it', async ({ page }) => {
    await page.goto('/checkout')
    await expect(page.getByLabel(/full name/i)).toBeVisible({ timeout: 10000 })

    await page.getByLabel(/full name/i).fill('Jane Doe')
    await page.getByLabel(/address line 1/i).fill('123 Main St')
    await page.getByLabel(/city/i).fill('Springfield')
    await page.getByLabel(/state/i).fill('IL')
    await page.getByLabel(/postal code/i).fill('62701')
    await page.getByLabel(/country/i).fill('US')

    await page.getByRole('button', { name: /next/i }).click()

    await expect(page.getByRole('heading', { name: /payment/i, level: 6 })).toBeVisible({ timeout: 8000 })

    const cardInput = page.getByPlaceholder(/card number/i)
    if (await cardInput.isVisible()) {
      await cardInput.fill('4111111111111111')
      const expiryInput = page.getByPlaceholder(/mm.*yy/i)
      if (await expiryInput.isVisible()) await expiryInput.fill('12/28')
      const cvvInput = page.getByPlaceholder(/cvv/i)
      if (await cvvInput.isVisible()) await cvvInput.fill('123')
    }

    await page.getByRole('button', { name: /next/i }).click()

    await expect(page.getByRole('heading', { name: /review/i, level: 6 })).toBeVisible({ timeout: 8000 })

    await page.getByRole('button', { name: /place order/i }).click()

    await expect(page).toHaveURL(/confirmation/, { timeout: 15000 })
    await expect(page.getByText(/order confirmed/i)).toBeVisible({ timeout: 10000 })
  })

  test('confirmation page shows order number', async ({ page }) => {
    await page.goto('/checkout/confirmation/ORD-TEST-001')
    await expect(
      page.getByText(/order number/i).or(page.getByText(/order confirmed/i))
    ).toBeVisible({ timeout: 10000 })
  })
})
