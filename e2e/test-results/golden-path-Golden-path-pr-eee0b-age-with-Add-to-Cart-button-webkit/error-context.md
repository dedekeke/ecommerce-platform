# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: golden-path.spec.ts >> Golden path: product list → cart → checkout → confirmation >> clicking a product opens the detail page with Add to Cart button
- Location: tests/golden-path.spec.ts:33:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-testid="product-card"]').first()
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for locator('[data-testid="product-card"]').first()

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - banner [ref=e4]:
    - generic [ref=e5]:
      - link "E-Commerce" [ref=e6]:
        - /url: /
      - generic [ref=e7]:
        - link "Home" [ref=e8]:
          - /url: /
        - link "Products" [ref=e9]:
          - /url: /products
        - link "Categories" [ref=e10]:
          - /url: /categories
      - generic [ref=e12]:
        - generic:
          - img
        - textbox "search" [ref=e14]:
          - /placeholder: Search products...
      - generic [ref=e15]:
        - button "Switch to dark mode" [ref=e16] [cursor=pointer]:
          - img [ref=e17]
        - link "shopping cart" [ref=e19] [cursor=pointer]:
          - /url: /cart
          - generic [ref=e20]:
            - img [ref=e21]
            - generic: "0"
        - generic [ref=e23]:
          - generic [ref=e24]: Language
          - generic [ref=e25]:
            - combobox "Language" [ref=e26] [cursor=pointer]: EN
            - textbox: en
            - img
            - group:
              - generic: Language
        - generic [ref=e27]:
          - generic [ref=e28]: Currency
          - generic [ref=e29]:
            - combobox "Currency" [ref=e30] [cursor=pointer]: USD
            - textbox: USD
            - img
            - group:
              - generic: Currency
        - button "Log In" [ref=e31] [cursor=pointer]
  - main [ref=e32]:
    - generic [ref=e36]:
      - navigation [ref=e37]:
        - list [ref=e38]:
          - listitem [ref=e39]:
            - link "Home" [ref=e40]:
              - /url: /
              - img [ref=e41]
              - text: Home
          - listitem [ref=e43]: /
          - listitem [ref=e44]:
            - paragraph [ref=e45]: Products
      - generic [ref=e46]:
        - generic [ref=e47]:
          - heading "All Products" [level=4] [ref=e48]
          - paragraph [ref=e49]: 0 products found
        - generic [ref=e50]:
          - generic [ref=e51]: Sort by
          - generic [ref=e52]:
            - combobox "Sort by" [ref=e53] [cursor=pointer]: Newest First
            - textbox: newest
            - img
            - group:
              - generic: Sort by
      - generic [ref=e54]:
        - heading "Failed to load products" [level=6] [ref=e55]
        - paragraph [ref=e56]: Please try again later
  - contentinfo [ref=e57]:
    - generic [ref=e58]:
      - generic [ref=e59]:
        - generic [ref=e60]:
          - heading "Customer Service" [level=6] [ref=e61]
          - navigation [ref=e62]:
            - link "Contact Us" [ref=e63]:
              - /url: /contact
            - link "FAQ" [ref=e64]:
              - /url: /faq
            - link "Shipping Info" [ref=e65]:
              - /url: /shipping
            - link "Returns" [ref=e66]:
              - /url: /returns
        - generic [ref=e67]:
          - heading "Company" [level=6] [ref=e68]
          - navigation [ref=e69]:
            - link "About Us" [ref=e70]:
              - /url: /about
            - link "Careers" [ref=e71]:
              - /url: /careers
            - link "Press" [ref=e72]:
              - /url: /press
        - generic [ref=e73]:
          - heading "Legal" [level=6] [ref=e74]
          - navigation [ref=e75]:
            - link "Privacy Policy" [ref=e76]:
              - /url: /privacy
            - link "Terms of Service" [ref=e77]:
              - /url: /terms
            - link "Cookie Policy" [ref=e78]:
              - /url: /cookies
        - generic [ref=e79]:
          - heading "Connect With Us" [level=6] [ref=e80]
          - generic [ref=e81]:
            - button "Facebook" [ref=e82] [cursor=pointer]:
              - img [ref=e83]
            - button "Twitter" [ref=e85] [cursor=pointer]:
              - img [ref=e86]
            - button "Instagram" [ref=e88] [cursor=pointer]:
              - img [ref=e89]
          - paragraph [ref=e91]: Subscribe to our newsletter for updates and exclusive offers.
      - separator [ref=e92]
      - generic [ref=e93]:
        - paragraph [ref=e94]: © 2025 E-Commerce Platform. All rights reserved.
        - paragraph [ref=e95]: Built with React 19 & Spring Boot
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
> 36  |     await expect(firstCard).toBeVisible({ timeout: 10000 })
      |                             ^ Error: expect(locator).toBeVisible() failed
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
  71  |     await expect(page.getByRole('heading', { name: /checkout/i })).toBeVisible({ timeout: 10000 })
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