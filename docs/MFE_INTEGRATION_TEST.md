# MFE Integration Smoke Test Playbook

> Back to [README](../README.md).

Run this manually after any change to the MFE federation config or shared store logic.

## Prerequisites

- Node 20+ installed
- All MFE `node_modules` installed (`npm install` in each frontend subdirectory)
- No port conflicts on 5001–5005, 5173
- `npx serve` available (zero-config static file server — installed on-demand via `npx --yes`)

## 1. Start all services

```bash
./scripts/run-frontend.sh
```

Wait for the terminal to print "All services started."

**Dependency order**: Angular MFEs on ports 5004 and 5005 start before the shell-app.
The native-federation runtime in `main.tsx` fetches `remoteEntry.json` from both Angular
MFEs during shell initialisation. If either is unreachable, the shell still mounts
(failure is non-fatal) but Angular MFE routes will show an error placeholder.

## 2. Product Catalog — browse products

1. Open http://localhost:5173/products
2. Verify the Product Catalog MFE renders a grid of products.
3. Check the browser network tab: `remoteEntry.js` from port 5001 was loaded.

## 3. Add to cart — badge increments

1. Click "Add to Cart" on any product card.
2. The cart badge in the top-right header increments by 1.
3. Open DevTools → Application → Local Storage → `cart-storage`.
   The entry should contain the added product.

## 4. Cart page — view item

1. Navigate to http://localhost:5173/cart
2. Verify the Cart MFE renders with the item added in step 3.
3. The item name, price, and quantity 1 are visible.

## 5. Cart page — update quantity and remove

1. Increase quantity to 2 using the quantity stepper.
2. Verify the subtotal updates accordingly.
3. Click the remove (trash) icon.
4. Verify the cart shows "Your cart is empty."

## 6. Checkout flow — multi-step form

1. Add a product again, then click "Proceed to Checkout."
2. Confirm the browser URL changes to /checkout.
3. The Checkout MFE renders Step 1 (Shipping Address).
4. Fill in a dummy address (any values).
5. Click "Continue." Step 2 (Payment Method) renders.
6. Enter a dummy card number (e.g. 4111 1111 1111 1111, exp 12/30, cvv 123).
7. Click "Continue." Step 3 (Review Order) renders with the items.
8. Click "Place Order."

## 7. Order confirmation

1. URL changes to /checkout/confirmation/:orderId.
2. The Confirmation page renders with the order ID.
3. "Continue Shopping" link navigates back to /products.

## 8. Cross-tab sync (optional)

1. Open a second browser tab at http://localhost:5173.
2. Add a different product in the second tab.
3. Return to the first tab — the cart badge updates on next navigation
   (Zustand rehydrates from localStorage via the storage event listener).

## 9. Auth token propagation (requires Auth0 configured)

1. Log in via the Login button.
2. Open DevTools Network tab, filter by XHR.
3. Add a product; the POST /cart request includes `Authorization: Bearer <token>`.

## 10. User Dashboard — Angular MFE (NEW — Day 45)

**Prerequisites**: Auth0 login required (any valid user).

1. Log in via the Login button.
2. Navigate to http://localhost:5173/profile
3. Verify the Angular User Dashboard MFE mounts inside the React shell.
   - The Angular sidenav ("My Account") should be visible.
   - The main content area shows the user overview.
4. Check the browser network tab:
   - `http://localhost:5004/remoteEntry.json` was fetched at shell startup.
   - Angular chunk files (e.g. `main-*.js`) from port 5004 were loaded.
5. Navigate the Angular sidenav: Profile, Orders, Addresses, Wishlist links should
   respond (Angular router handles sub-routes internally via hash routing).
6. Navigate away (e.g. back to /products) and then return to /profile.
   The Angular MFE re-mounts cleanly.

## 11. Admin Dashboard — Angular MFE + RBAC (NEW — Day 45)

### 11a. Without admin role (access denied)

1. Log in with a regular (non-admin) user account.
2. Navigate to http://localhost:5173/admin/products
3. Verify a **403 Forbidden** page is shown, NOT the Admin Dashboard.
   - The page shows a "403" heading.
   - A "Go Home" link is visible and navigates to `/`.
4. The Angular Admin Dashboard MFE is never loaded (no request to port 5005 in
   the Network tab for this user).

### 11b. With admin role

1. Log in with a user that has the `admin` role in the Auth0 token.
   (The role claim path is `https://ecommerce-platform.com/roles`.)
2. Navigate to http://localhost:5173/admin/products
3. Verify the Angular Admin Dashboard MFE mounts:
   - The Angular sidenav ("Admin") should be visible.
   - Products list / Overview is shown.
4. Check the browser network tab:
   - `http://localhost:5005/remoteEntry.json` was fetched at shell startup.
   - Angular chunk files from port 5005 are loaded after navigating to /admin.
5. Navigate the admin sidenav: Overview, Products, Orders, Users, Analytics.

## 12. RBAC role claim path

The shell reads roles from the Auth0 JWT custom claim:

```
https://ecommerce-platform.com/roles
```

This must be added as a custom claim in the Auth0 dashboard (Rules or Actions)
for the role check to work. Example Auth0 Action:

```js
exports.onExecutePostLogin = async (event, api) => {
  const namespace = 'https://ecommerce-platform.com';
  if (event.authorization) {
    api.idToken.setCustomClaim(`${namespace}/roles`, event.authorization.roles);
    api.accessToken.setCustomClaim(`${namespace}/roles`, event.authorization.roles);
  }
};
```

## Tear down

Press Ctrl+C in the terminal running `run-frontend.sh`. All six processes stop cleanly.
