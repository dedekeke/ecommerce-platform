import { test, expect } from '@playwright/test'

/**
 * Cart edge cases: empty state, quantity stepper, remove item, persistence across reload.
 */

test.describe('Cart edge cases', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.evaluate(() => localStorage.removeItem('cart-storage'))
  })

  test('empty cart shows empty state message and not a checkout button', async ({ page }) => {
    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()
    await expect(page.getByText(/your cart is empty/i)).toBeVisible({ timeout: 8000 })
    await expect(page.getByRole('button', { name: /proceed to checkout/i })).not.toBeVisible()
  })

  test('quantity stepper increases item count', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('cart-storage', JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
          total: 10,
          itemCount: 1,
        },
        version: 0,
      }))
    })

    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()

    const increaseBtn = page.getByRole('button', { name: /increase quantity/i })
    await expect(increaseBtn).toBeVisible({ timeout: 8000 })
    await increaseBtn.click()

    const qtyInput = page.getByRole('spinbutton', { name: /quantity/i })
    await expect(qtyInput).toHaveValue('2', { timeout: 5000 })
  })

  test('quantity stepper decrease is disabled when quantity is 1', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('cart-storage', JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
          total: 10,
          itemCount: 1,
        },
        version: 0,
      }))
    })

    await page.goto('/cart')
    const decreaseBtn = page.getByRole('button', { name: /decrease quantity/i })
    await expect(decreaseBtn).toBeDisabled({ timeout: 8000 })
  })

  test('remove item from cart removes it from the list', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('cart-storage', JSON.stringify({
        state: {
          items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
          total: 10,
          itemCount: 1,
        },
        version: 0,
      }))
    })

    await page.goto('/cart')
    await expect(page.getByText('Test Product')).toBeVisible({ timeout: 8000 })

    const removeBtn = page.getByRole('button', { name: /remove test product/i })
    await expect(removeBtn).toBeVisible()
    await removeBtn.click()

    await expect(page.getByText(/your cart is empty/i)).toBeVisible({ timeout: 5000 })
  })

  test('cart persists across page reload', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('cart-storage', JSON.stringify({
        state: {
          items: [{ productId: 'p2', name: 'Persist Product', price: 25, quantity: 2, image: null }],
          total: 50,
          itemCount: 2,
        },
        version: 0,
      }))
    })

    await page.goto('/cart')
    await expect(page.getByText('Persist Product')).toBeVisible({ timeout: 8000 })

    await page.reload()
    await expect(page.getByText('Persist Product')).toBeVisible({ timeout: 8000 })
  })

  test('cart total updates correctly after quantity change', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem('cart-storage', JSON.stringify({
        state: {
          items: [{ productId: 'p3', name: 'Price Product', price: 20, quantity: 1, image: null }],
          total: 20,
          itemCount: 1,
        },
        version: 0,
      }))
    })

    await page.goto('/cart')
    // Assert on the ORDER SUMMARY grand total (data-testid="cart-total" in
    // cart-mfe CartSummary), not on any element that happens to show a price.
    // Grand total = subtotal + 10% tax + $5 shipping (< $50 free-shipping threshold).
    const grandTotal = page.getByTestId('cart-total')
    await expect(grandTotal).toHaveText('$27.00', { timeout: 8000 }) // 20 + 2 + 5

    const increaseBtn = page.getByRole('button', { name: /increase quantity/i })
    await increaseBtn.click()

    await expect(grandTotal).toHaveText('$49.00', { timeout: 5000 }) // 40 + 4 + 5
  })
})
