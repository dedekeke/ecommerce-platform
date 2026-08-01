# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: a11y.spec.ts >> Accessibility: WCAG 2.1 AA >> navigation header has no WCAG 2.1 AA violations
- Location: tests/a11y.spec.ts:91:3

# Error details

```
Error: frame.evaluate: Error: No elements found for include in page Context
    at validateContext (eval at evaluate (:302:30), <anonymous>:18986:15)
    at new Context (eval at evaluate (:302:30), <anonymous>:18967:7)
    at Object._getFrameContexts (eval at evaluate (:302:30), <anonymous>:19008:22)
    at eval (eval at evaluate (:302:30), <anonymous>:4:27)
    at UtilityScript.evaluate (<anonymous>:304:16)
    at UtilityScript.<anonymous> (<anonymous>:1:44)
```