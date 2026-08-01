/**
 * Reads a Vite-style env var defensively. This MFE is built with Angular's
 * esbuild/native-federation builder rather than Vite, so `import.meta.env` is not
 * populated at runtime today — mirrors the same defensive pattern used by
 * {@link ../services/wishlist.service.ts} for VITE_WISHLIST_BACKEND_ENABLED.
 */
function readEnv(key: string): string {
  try {
    const meta = (import.meta as unknown) as { env?: Record<string, string | undefined> };
    return meta?.env?.[key] ?? '';
  } catch {
    return '';
  }
}

/**
 * Stripe publishable (client-side) key, pk_.... Sourced from VITE_STRIPE_PUBLISHABLE_KEY to
 * match the convention used by checkout-mfe/src/config/payments.ts. Never hardcode a real key
 * here — an empty value disables the add-card flow with a "Payment is not configured" message
 * instead of throwing (see StripeLoaderService.load).
 */
export const STRIPE_PUBLISHABLE_KEY: string = readEnv('VITE_STRIPE_PUBLISHABLE_KEY');
