# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: cart-edge-cases.spec.ts >> Cart edge cases >> cart persists across page reload
- Location: tests/cart-edge-cases.spec.ts:82:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Persist Product')
Expected: visible
Timeout: 8000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 8000ms
  - waiting for getByText('Persist Product')

```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test'
  2   | 
  3   | /**
  4   |  * Cart edge cases: empty state, quantity stepper, remove item, persistence across reload.
  5   |  */
  6   | 
  7   | test.describe('Cart edge cases', () => {
  8   |   test.beforeEach(async ({ page }) => {
  9   |     await page.goto('/')
  10  |     await page.evaluate(() => localStorage.removeItem('cart-storage'))
  11  |   })
  12  | 
  13  |   test('empty cart shows empty state message and not a checkout button', async ({ page }) => {
  14  |     await page.goto('/cart')
  15  |     await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()
  16  |     await expect(page.getByText(/your cart is empty/i)).toBeVisible({ timeout: 8000 })
  17  |     await expect(page.getByRole('button', { name: /proceed to checkout/i })).not.toBeVisible()
  18  |   })
  19  | 
  20  |   test('quantity stepper increases item count', async ({ page }) => {
  21  |     await page.evaluate(() => {
  22  |       localStorage.setItem('cart-storage', JSON.stringify({
  23  |         state: {
  24  |           items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
  25  |           total: 10,
  26  |           itemCount: 1,
  27  |         },
  28  |         version: 0,
  29  |       }))
  30  |     })
  31  | 
  32  |     await page.goto('/cart')
  33  |     await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()
  34  | 
  35  |     const increaseBtn = page.getByRole('button', { name: /increase quantity/i })
  36  |     await expect(increaseBtn).toBeVisible({ timeout: 8000 })
  37  |     await increaseBtn.click()
  38  | 
  39  |     const qtyInput = page.getByLabel(/quantity/i)
  40  |     await expect(qtyInput).toHaveValue('2', { timeout: 5000 })
  41  |   })
  42  | 
  43  |   test('quantity stepper decrease is disabled when quantity is 1', async ({ page }) => {
  44  |     await page.evaluate(() => {
  45  |       localStorage.setItem('cart-storage', JSON.stringify({
  46  |         state: {
  47  |           items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
  48  |           total: 10,
  49  |           itemCount: 1,
  50  |         },
  51  |         version: 0,
  52  |       }))
  53  |     })
  54  | 
  55  |     await page.goto('/cart')
  56  |     const decreaseBtn = page.getByRole('button', { name: /decrease quantity/i })
  57  |     await expect(decreaseBtn).toBeDisabled({ timeout: 8000 })
  58  |   })
  59  | 
  60  |   test('remove item from cart removes it from the list', async ({ page }) => {
  61  |     await page.evaluate(() => {
  62  |       localStorage.setItem('cart-storage', JSON.stringify({
  63  |         state: {
  64  |           items: [{ productId: 'p1', name: 'Test Product', price: 10, quantity: 1, image: null }],
  65  |           total: 10,
  66  |           itemCount: 1,
  67  |         },
  68  |         version: 0,
  69  |       }))
  70  |     })
  71  | 
  72  |     await page.goto('/cart')
  73  |     await expect(page.getByText('Test Product')).toBeVisible({ timeout: 8000 })
  74  | 
  75  |     const removeBtn = page.getByRole('button', { name: /remove test product/i })
  76  |     await expect(removeBtn).toBeVisible()
  77  |     await removeBtn.click()
  78  | 
  79  |     await expect(page.getByText(/your cart is empty/i)).toBeVisible({ timeout: 5000 })
  80  |   })
  81  | 
  82  |   test('cart persists across page reload', async ({ page }) => {
  83  |     await page.evaluate(() => {
  84  |       localStorage.setItem('cart-storage', JSON.stringify({
  85  |         state: {
  86  |           items: [{ productId: 'p2', name: 'Persist Product', price: 25, quantity: 2, image: null }],
  87  |           total: 50,
  88  |           itemCount: 2,
  89  |         },
  90  |         version: 0,
  91  |       }))
  92  |     })
  93  | 
  94  |     await page.goto('/cart')
> 95  |     await expect(page.getByText('Persist Product')).toBeVisible({ timeout: 8000 })
      |                                                     ^ Error: expect(locator).toBeVisible() failed
  96  | 
  97  |     await page.reload()
  98  |     await expect(page.getByText('Persist Product')).toBeVisible({ timeout: 8000 })
  99  |   })
  100 | 
  101 |   test('cart total updates correctly after quantity change', async ({ page }) => {
  102 |     await page.evaluate(() => {
  103 |       localStorage.setItem('cart-storage', JSON.stringify({
  104 |         state: {
  105 |           items: [{ productId: 'p3', name: 'Price Product', price: 20, quantity: 1, image: null }],
  106 |           total: 20,
  107 |           itemCount: 1,
  108 |         },
  109 |         version: 0,
  110 |       }))
  111 |     })
  112 | 
  113 |     await page.goto('/cart')
  114 |     await expect(page.getByText('$20.00')).toBeVisible({ timeout: 8000 })
  115 | 
  116 |     const increaseBtn = page.getByRole('button', { name: /increase quantity/i })
  117 |     await increaseBtn.click()
  118 | 
  119 |     await expect(page.getByText('$40.00')).toBeVisible({ timeout: 5000 })
  120 |   })
  121 | })
  122 | 
```