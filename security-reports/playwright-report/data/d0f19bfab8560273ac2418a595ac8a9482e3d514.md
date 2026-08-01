# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: a11y.spec.ts >> Accessibility: WCAG 2.1 AA >> /cart (empty) has no WCAG 2.1 AA violations
- Location: tests/a11y.spec.ts:51:3

# Error details

```
Error: [
  {
    "id": "aria-roles",
    "impact": "critical",
    "tags": [
      "cat.aria",
      "wcag2a",
      "wcag412",
      "EN-301-549",
      "EN-9.4.1.2",
      "RGAAv4",
      "RGAA-7.1.1"
    ],
    "description": "Ensure all elements with a role attribute use a valid value",
    "help": "ARIA roles used must conform to valid values",
    "helpUrl": "https://dequeuniversity.com/rules/axe/4.11/aria-roles?application=playwright",
    "nodes": [
      {
        "any": [],
        "all": [],
        "none": [
          {
            "id": "invalidrole",
            "data": [
              "bar"
            ],
            "relatedNodes": [],
            "impact": "critical",
            "message": "Role must be one of the valid ARIA roles: bar"
          }
        ],
        "impact": "critical",
        "html": "<div class=\"bar\" role=\"bar\" style=\"transform: translate3d(0%, 0px, 0px); transition: 200ms;\"><div class=\"peg\"></div></div>",
        "target": [
          ".bar"
        ],
        "failureSummary": "Fix all of the following:\n  Role must be one of the valid ARIA roles: bar"
      }
    ]
  }
]

expect(received).toHaveLength(expected)

Expected length: 0
Received length: 1
Received array:  [{"description": "Ensure all elements with a role attribute use a valid value", "help": "ARIA roles used must conform to valid values", "helpUrl": "https://dequeuniversity.com/rules/axe/4.11/aria-roles?application=playwright", "id": "aria-roles", "impact": "critical", "nodes": [{"all": [], "any": [], "failureSummary": "Fix all of the following:
  Role must be one of the valid ARIA roles: bar", "html": "<div class=\"bar\" role=\"bar\" style=\"transform: translate3d(0%, 0px, 0px); transition: 200ms;\"><div class=\"peg\"></div></div>", "impact": "critical", "none": [{"data": ["bar"], "id": "invalidrole", "impact": "critical", "message": "Role must be one of the valid ARIA roles: bar", "relatedNodes": []}], "target": [".bar"]}], "tags": ["cat.aria", "wcag2a", "wcag412", "EN-301-549", "EN-9.4.1.2", "RGAAv4", "RGAA-7.1.1"]}]
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
      - heading "Shopping Cart" [level=4] [ref=e37]
      - generic [ref=e40]:
        - img [ref=e41]
        - heading "Your cart is empty" [level=5] [ref=e43]
        - paragraph [ref=e44]: Looks like you haven't added anything yet. Explore our catalog to find something you'll love.
        - link "Continue shopping" [ref=e45] [cursor=pointer]:
          - /url: /products
  - contentinfo [ref=e46]:
    - generic [ref=e47]:
      - generic [ref=e48]:
        - generic [ref=e49]:
          - heading "Customer Service" [level=6] [ref=e50]
          - navigation [ref=e51]:
            - link "Contact Us" [ref=e52]:
              - /url: /contact
            - link "FAQ" [ref=e53]:
              - /url: /faq
            - link "Shipping Info" [ref=e54]:
              - /url: /shipping
            - link "Returns" [ref=e55]:
              - /url: /returns
        - generic [ref=e56]:
          - heading "Company" [level=6] [ref=e57]
          - navigation [ref=e58]:
            - link "About Us" [ref=e59]:
              - /url: /about
            - link "Careers" [ref=e60]:
              - /url: /careers
            - link "Press" [ref=e61]:
              - /url: /press
        - generic [ref=e62]:
          - heading "Legal" [level=6] [ref=e63]
          - navigation [ref=e64]:
            - link "Privacy Policy" [ref=e65]:
              - /url: /privacy
            - link "Terms of Service" [ref=e66]:
              - /url: /terms
            - link "Cookie Policy" [ref=e67]:
              - /url: /cookies
        - generic [ref=e68]:
          - heading "Connect With Us" [level=6] [ref=e69]
          - generic [ref=e70]:
            - button "Facebook" [ref=e71] [cursor=pointer]:
              - img [ref=e72]
            - button "Twitter" [ref=e74] [cursor=pointer]:
              - img [ref=e75]
            - button "Instagram" [ref=e77] [cursor=pointer]:
              - img [ref=e78]
          - paragraph [ref=e80]: Subscribe to our newsletter for updates and exclusive offers.
      - separator [ref=e81]
      - generic [ref=e82]:
        - paragraph [ref=e83]: © 2025 E-Commerce Platform. All rights reserved.
        - paragraph [ref=e84]: Built with React 19 & Spring Boot
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test'
  2   | import AxeBuilder from '@axe-core/playwright'
  3   | 
  4   | /**
  5   |  * Accessibility audit using axe-core against WCAG 2.1 AA.
  6   |  * Each major route is visited and scanned.
  7   |  *
  8   |  * Allow-listed violations are documented with the reason they are acceptable:
  9   |  *
  10  |  * - "color-contrast" on the MUI skeleton pulse animation: MUI intentionally uses
  11  |  *   low-contrast placeholder colours during loading; these are transient states
  12  |  *   and do not convey information.  Resolved once real content loads.
  13  |  *
  14  |  * - "scrollable-region-focusable": the MUI DataGrid scroll container is a known
  15  |  *   upstream MUI issue tracked at https://github.com/mui/mui-x/issues/12345.
  16  |  *   This will be fixed when the MUI DataGrid reaches ARIA grid conformance.
  17  |  */
  18  | 
  19  | const WCAG_21_AA_TAGS = ['wcag2a', 'wcag2aa', 'wcag21aa']
  20  | 
  21  | const ALLOWED_VIOLATION_IDS: string[] = [
  22  |   'color-contrast',
  23  | ]
  24  | 
  25  | function filterViolations(violations: { id: string }[]) {
  26  |   return violations.filter((v) => !ALLOWED_VIOLATION_IDS.includes(v.id))
  27  | }
  28  | 
  29  | async function runAxeScan(page: Parameters<typeof AxeBuilder['new']>[0]) {
  30  |   const results = await new AxeBuilder({ page })
  31  |     .withTags(WCAG_21_AA_TAGS)
  32  |     .analyze()
  33  |   return filterViolations(results.violations)
  34  | }
  35  | 
  36  | test.describe('Accessibility: WCAG 2.1 AA', () => {
  37  |   test('homepage has no WCAG 2.1 AA violations', async ({ page }) => {
  38  |     await page.goto('/')
  39  |     await page.waitForLoadState('networkidle')
  40  |     const violations = await runAxeScan(page)
  41  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  42  |   })
  43  | 
  44  |   test('/products has no WCAG 2.1 AA violations', async ({ page }) => {
  45  |     await page.goto('/products')
  46  |     await page.waitForLoadState('networkidle')
  47  |     const violations = await runAxeScan(page)
  48  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  49  |   })
  50  | 
  51  |   test('/cart (empty) has no WCAG 2.1 AA violations', async ({ page }) => {
  52  |     await page.goto('/')
  53  |     await page.evaluate(() => localStorage.removeItem('cart-storage'))
  54  |     await page.goto('/cart')
  55  |     await page.waitForLoadState('networkidle')
  56  |     const violations = await runAxeScan(page)
> 57  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
      |                                                             ^ Error: [
  58  |   })
  59  | 
  60  |   test('/cart (with items) has no WCAG 2.1 AA violations', async ({ page }) => {
  61  |     await page.evaluate(() => {
  62  |       localStorage.setItem('cart-storage', JSON.stringify({
  63  |         state: {
  64  |           items: [{ productId: 'a11y-1', name: 'A11y Test Product', price: 15, quantity: 1, image: null }],
  65  |           total: 15,
  66  |           itemCount: 1,
  67  |         },
  68  |         version: 0,
  69  |       }))
  70  |     })
  71  |     await page.goto('/cart')
  72  |     await page.waitForLoadState('networkidle')
  73  |     const violations = await runAxeScan(page)
  74  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  75  |   })
  76  | 
  77  |   test('/checkout has no WCAG 2.1 AA violations', async ({ page }) => {
  78  |     await page.goto('/checkout')
  79  |     await page.waitForLoadState('networkidle')
  80  |     const violations = await runAxeScan(page)
  81  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  82  |   })
  83  | 
  84  |   test('/admin (403 page) has no WCAG 2.1 AA violations', async ({ page }) => {
  85  |     await page.goto('/admin')
  86  |     await page.waitForLoadState('networkidle')
  87  |     const violations = await runAxeScan(page)
  88  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  89  |   })
  90  | 
  91  |   test('navigation header has no WCAG 2.1 AA violations', async ({ page }) => {
  92  |     await page.goto('/')
  93  |     await page.waitForLoadState('networkidle')
  94  |     const violations = await new AxeBuilder({ page })
  95  |       .withTags(WCAG_21_AA_TAGS)
  96  |       .include('header')
  97  |       .analyze()
  98  |     const filtered = filterViolations(violations.violations)
  99  |     expect(filtered, JSON.stringify(filtered, null, 2)).toHaveLength(0)
  100 |   })
  101 | })
  102 | 
```