# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: golden-path.spec.ts >> Golden path: product list → cart → checkout → confirmation >> checkout page renders shipping address step
- Location: tests/golden-path.spec.ts:69:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('heading', { name: /checkout/i })
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByRole('heading', { name: /checkout/i })
    - waiting for navigation to finish...
    - navigated to "https://dev-vjhkmx73y28ucgcb.us.auth0.com/authorize?client_id=2meu7Nutg5bTbQPUcLXP3S2ywSdTDBVT&scope=openid+profile+email+offline_access&redirect_uri=http%3A%2F%2Flocalhost%3A5173&audience=https%3A%2…"

```

# Page snapshot

```yaml
- generic [ref=e2]:
  - img [ref=e4]
  - generic [ref=e23]:
    - generic [ref=e25]:
      - generic [ref=e26]:
        - generic [ref=e27]: Error
        - heading "Access denied." [level=1] [ref=e28]
      - generic [ref=e30]:
        - paragraph [ref=e31]: To protect the requested service, this request has been blocked or was unable to complete for other reasons.
        - generic [ref=e32]:
          - generic [ref=e34]:
            - generic [ref=e35]: Your IP Address
            - generic [ref=e37]: 2001:ee0:4f83:5c20:a04e:ed85:6b61:adcb
          - generic [ref=e39]:
            - generic [ref=e40]: Request ID
            - generic [ref=e42]: a0ba853a0b0f22a1
    - img [ref=e44]
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test'
  2   | 
  3   | /**
  4   |  * Golden path: product list → add to cart → checkout → confirmation.
  5   |  *
  6   |  * Pre-conditions (see e2e/README.md):
  7   |  *   - shell-app running on http://localhost:5173
  8   |  *   - product-catalog-mfe running on http://localhost:5001
  9   |  *   - cart-mfe running on http://localhost:5002
  10  |  *   - checkout-mfe running on http://localhost:5003
  11  |  *   - API gateway running on http://localhost:8080
  12  |  */
  13  | 
  14  | test.describe('Golden path: product list → cart → checkout → confirmation', () => {
  15  |   test.beforeEach(async ({ page }) => {
  16  |     await page.goto('/')
  17  |     await page.evaluate(() => localStorage.removeItem('cart-storage'))
  18  |   })
  19  | 
  20  |   test('homepage renders welcome heading and category links', async ({ page }) => {
  21  |     await page.goto('/')
  22  |     await expect(page.getByRole('heading', { name: /welcome to e-commerce/i })).toBeVisible()
  23  |     await expect(page.getByRole('link', { name: /electronics/i })).toBeVisible()
  24  |     await expect(page.getByRole('link', { name: /fashion/i })).toBeVisible()
  25  |     await expect(page.getByRole('link', { name: /home & garden/i })).toBeVisible()
  26  |   })
  27  | 
  28  |   test('navigating to /products renders the product list page', async ({ page }) => {
  29  |     await page.goto('/products')
  30  |     await expect(page.getByRole('heading', { name: /all products/i })).toBeVisible({ timeout: 10000 })
  31  |   })
  32  | 
  33  |   test('clicking a product opens the detail page with Add to Cart button', async ({ page }) => {
  34  |     await page.goto('/products')
  35  |     const firstCard = page.locator('[data-testid="product-card"]').first()
  36  |     await expect(firstCard).toBeVisible({ timeout: 10000 })
  37  |     await firstCard.click()
  38  |     await expect(page.getByRole('button', { name: /add to cart/i })).toBeVisible({ timeout: 8000 })
  39  |   })
  40  | 
  41  |   test('add to cart increments header cart badge', async ({ page }) => {
  42  |     await page.goto('/products')
  43  |     const firstCard = page.locator('[data-testid="product-card"]').first()
  44  |     await expect(firstCard).toBeVisible({ timeout: 10000 })
  45  |     await firstCard.click()
  46  | 
  47  |     const addBtn = page.getByRole('button', { name: /add to cart/i })
  48  |     await expect(addBtn).toBeEnabled({ timeout: 8000 })
  49  |     await addBtn.click()
  50  | 
  51  |     await expect(page.locator('[data-testid="cart-badge"]')).toContainText('1', { timeout: 5000 })
  52  |   })
  53  | 
  54  |   test('cart page shows added item and Proceed to Checkout button', async ({ page }) => {
  55  |     await page.goto('/products')
  56  |     const firstCard = page.locator('[data-testid="product-card"]').first()
  57  |     await expect(firstCard).toBeVisible({ timeout: 10000 })
  58  |     await firstCard.click()
  59  | 
  60  |     const addBtn = page.getByRole('button', { name: /add to cart/i })
  61  |     await expect(addBtn).toBeEnabled({ timeout: 8000 })
  62  |     await addBtn.click()
  63  | 
  64  |     await page.goto('/cart')
  65  |     await expect(page.getByRole('heading', { name: /shopping cart/i })).toBeVisible()
  66  |     await expect(page.getByRole('button', { name: /proceed to checkout/i })).toBeVisible({ timeout: 8000 })
  67  |   })
  68  | 
  69  |   test('checkout page renders shipping address step', async ({ page }) => {
  70  |     await page.goto('/checkout')
> 71  |     await expect(page.getByRole('heading', { name: /checkout/i })).toBeVisible({ timeout: 10000 })
      |                                                                    ^ Error: expect(locator).toBeVisible() failed
  72  |     await expect(page.getByLabel(/full name/i)).toBeVisible()
  73  |   })
  74  | 
  75  |   test('address form validates required fields on blur', async ({ page }) => {
  76  |     await page.goto('/checkout')
  77  |     await expect(page.getByLabel(/full name/i)).toBeVisible({ timeout: 10000 })
  78  | 
  79  |     const fullNameInput = page.getByLabel(/full name/i)
  80  |     await fullNameInput.focus()
  81  |     await fullNameInput.blur()
  82  |     await expect(page.getByRole('alert').filter({ hasText: /full name is required/i })).toBeVisible()
  83  |   })
  84  | 
  85  |   test('full checkout flow fills address, payment, reviews order and places it', async ({ page }) => {
  86  |     await page.goto('/checkout')
  87  |     await expect(page.getByLabel(/full name/i)).toBeVisible({ timeout: 10000 })
  88  | 
  89  |     await page.getByLabel(/full name/i).fill('Jane Doe')
  90  |     await page.getByLabel(/address line 1/i).fill('123 Main St')
  91  |     await page.getByLabel(/city/i).fill('Springfield')
  92  |     await page.getByLabel(/state/i).fill('IL')
  93  |     await page.getByLabel(/postal code/i).fill('62701')
  94  |     await page.getByLabel(/country/i).fill('US')
  95  | 
  96  |     await page.getByRole('button', { name: /next/i }).click()
  97  | 
  98  |     await expect(page.getByRole('heading', { name: /payment/i, level: 6 })).toBeVisible({ timeout: 8000 })
  99  | 
  100 |     const cardInput = page.getByPlaceholder(/card number/i)
  101 |     if (await cardInput.isVisible()) {
  102 |       await cardInput.fill('4111111111111111')
  103 |       const expiryInput = page.getByPlaceholder(/mm.*yy/i)
  104 |       if (await expiryInput.isVisible()) await expiryInput.fill('12/28')
  105 |       const cvvInput = page.getByPlaceholder(/cvv/i)
  106 |       if (await cvvInput.isVisible()) await cvvInput.fill('123')
  107 |     }
  108 | 
  109 |     await page.getByRole('button', { name: /next/i }).click()
  110 | 
  111 |     await expect(page.getByRole('heading', { name: /review/i, level: 6 })).toBeVisible({ timeout: 8000 })
  112 | 
  113 |     await page.getByRole('button', { name: /place order/i }).click()
  114 | 
  115 |     await expect(page).toHaveURL(/confirmation/, { timeout: 15000 })
  116 |     await expect(page.getByText(/order confirmed/i)).toBeVisible({ timeout: 10000 })
  117 |   })
  118 | 
  119 |   test('confirmation page shows order number', async ({ page }) => {
  120 |     await page.goto('/checkout/confirmation/ORD-TEST-001')
  121 |     await expect(
  122 |       page.getByText(/order number/i).or(page.getByText(/order confirmed/i))
  123 |     ).toBeVisible({ timeout: 10000 })
  124 |   })
  125 | })
  126 | 
```