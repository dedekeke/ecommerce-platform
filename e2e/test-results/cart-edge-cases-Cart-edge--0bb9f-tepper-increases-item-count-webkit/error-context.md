# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: cart-edge-cases.spec.ts >> Cart edge cases >> quantity stepper increases item count
- Location: tests/cart-edge-cases.spec.ts:20:3

# Error details

```
Error: expect(locator).toHaveValue(expected) failed

Locator: getByLabel(/quantity/i)
Expected: "2"
Error: strict mode violation: getByLabel(/quantity/i) resolved to 3 elements:
    1) <button tabindex="0" type="button" aria-label="Decrease quantity" class="MuiButtonBase-root MuiIconButton-root MuiIconButton-sizeSmall css-1kr8x2x">…</button> aka getByRole('button', { name: 'Decrease quantity' })
    2) <input readonly value="2" type="number" aria-label="Quantity" class="MuiBox-root css-6113ix"/> aka getByRole('spinbutton', { name: 'Quantity' })
    3) <button tabindex="0" type="button" aria-label="Increase quantity" class="MuiButtonBase-root MuiIconButton-root MuiIconButton-sizeSmall css-1kr8x2x">…</button> aka getByRole('button', { name: 'Increase quantity' })

Call log:
  - Expect "toHaveValue" with timeout 5000ms
  - waiting for getByLabel(/quantity/i)

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
            - generic [ref=e23]: "1"
        - generic [ref=e24]:
          - generic [ref=e25]: Language
          - generic [ref=e26]:
            - combobox "Language" [ref=e27] [cursor=pointer]: EN
            - textbox: en
            - img
            - group:
              - generic: Language
        - generic [ref=e28]:
          - generic [ref=e29]: Currency
          - generic [ref=e30]:
            - combobox "Currency" [ref=e31] [cursor=pointer]: USD
            - textbox: USD
            - img
            - group:
              - generic: Currency
        - button "Log In" [ref=e32] [cursor=pointer]
  - main [ref=e33]:
    - generic [ref=e37]:
      - heading "Shopping Cart(1 item)" [level=4] [ref=e38]:
        - text: Shopping Cart
        - generic [ref=e39]: (1 item)
      - generic [ref=e40]:
        - generic [ref=e43]:
          - generic [ref=e44]:
            - img "Test Product" [ref=e45]
            - generic [ref=e46]:
              - paragraph [ref=e47]: Test Product
              - paragraph [ref=e48]: $10.00
            - generic [ref=e49]:
              - button "Decrease quantity" [ref=e50] [cursor=pointer]:
                - img [ref=e51]
              - spinbutton "Quantity" [ref=e53]: "2"
              - button "Increase quantity" [active] [ref=e54] [cursor=pointer]:
                - img [ref=e55]
            - paragraph [ref=e57]: $20.00
            - button "Remove Test Product" [ref=e58] [cursor=pointer]:
              - img [ref=e59]
          - separator [ref=e61]
        - generic [ref=e64]:
          - heading "Order Summary" [level=6] [ref=e65]
          - separator [ref=e66]
          - generic [ref=e67]:
            - generic [ref=e68]:
              - paragraph [ref=e69]: Subtotal
              - paragraph [ref=e70]: $20.00
            - generic [ref=e71]:
              - paragraph [ref=e72]: Est. Tax (10%)
              - paragraph [ref=e73]: $2.00
            - generic [ref=e74]:
              - paragraph [ref=e75]: Shipping
              - paragraph [ref=e76]: $5.00
          - generic [ref=e77]:
            - img [ref=e78]
            - generic [ref=e80]: Add $30.00 more for free shipping
          - separator [ref=e81]
          - generic [ref=e82]:
            - paragraph [ref=e83]: Total
            - paragraph [ref=e84]: $27.00
          - button "Proceed to checkout" [ref=e85] [cursor=pointer]
  - contentinfo [ref=e86]:
    - generic [ref=e87]:
      - generic [ref=e88]:
        - generic [ref=e89]:
          - heading "Customer Service" [level=6] [ref=e90]
          - navigation [ref=e91]:
            - link "Contact Us" [ref=e92]:
              - /url: /contact
            - link "FAQ" [ref=e93]:
              - /url: /faq
            - link "Shipping Info" [ref=e94]:
              - /url: /shipping
            - link "Returns" [ref=e95]:
              - /url: /returns
        - generic [ref=e96]:
          - heading "Company" [level=6] [ref=e97]
          - navigation [ref=e98]:
            - link "About Us" [ref=e99]:
              - /url: /about
            - link "Careers" [ref=e100]:
              - /url: /careers
            - link "Press" [ref=e101]:
              - /url: /press
        - generic [ref=e102]:
          - heading "Legal" [level=6] [ref=e103]
          - navigation [ref=e104]:
            - link "Privacy Policy" [ref=e105]:
              - /url: /privacy
            - link "Terms of Service" [ref=e106]:
              - /url: /terms
            - link "Cookie Policy" [ref=e107]:
              - /url: /cookies
        - generic [ref=e108]:
          - heading "Connect With Us" [level=6] [ref=e109]
          - generic [ref=e110]:
            - button "Facebook" [ref=e111] [cursor=pointer]:
              - img [ref=e112]
            - button "Twitter" [ref=e114] [cursor=pointer]:
              - img [ref=e115]
            - button "Instagram" [ref=e117] [cursor=pointer]:
              - img [ref=e118]
          - paragraph [ref=e120]: Subscribe to our newsletter for updates and exclusive offers.
      - separator [ref=e121]
      - generic [ref=e122]:
        - paragraph [ref=e123]: © 2025 E-Commerce Platform. All rights reserved.
        - paragraph [ref=e124]: Built with React 19 & Spring Boot
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
> 40  |     await expect(qtyInput).toHaveValue('2', { timeout: 5000 })
      |                            ^ Error: expect(locator).toHaveValue(expected) failed
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
  95  |     await expect(page.getByText('Persist Product')).toBeVisible({ timeout: 8000 })
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