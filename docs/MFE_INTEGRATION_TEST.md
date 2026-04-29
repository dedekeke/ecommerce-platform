# MFE Integration Smoke Test Playbook

Run this manually after any change to the MFE federation config or shared store logic.

## Prerequisites

- Node 20+ installed
- All MFE `node_modules` installed (`npm install` in each frontend subdirectory)
- No port conflicts on 5001–5003, 5173

## 1. Start all services

```bash
./scripts/run-frontend.sh
```

Wait for the terminal to print "All services started."

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

## Tear down

Press Ctrl+C in the terminal running `run-frontend.sh`. All four processes stop cleanly.
