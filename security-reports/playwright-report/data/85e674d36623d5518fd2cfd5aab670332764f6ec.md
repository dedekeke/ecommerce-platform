# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: a11y.spec.ts >> Accessibility: WCAG 2.1 AA >> /cart (with items) has no WCAG 2.1 AA violations
- Location: tests/a11y.spec.ts:60:3

# Error details

```
Error: page.evaluate: SecurityError: The operation is insecure.
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
  57  |     expect(violations, JSON.stringify(violations, null, 2)).toHaveLength(0)
  58  |   })
  59  | 
  60  |   test('/cart (with items) has no WCAG 2.1 AA violations', async ({ page }) => {
> 61  |     await page.evaluate(() => {
      |                ^ Error: page.evaluate: SecurityError: The operation is insecure.
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