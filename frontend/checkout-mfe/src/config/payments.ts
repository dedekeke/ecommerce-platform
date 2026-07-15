/**
 * Payment configuration sourced from Vite env vars.
 *
 * Stripe Elements is the only supported checkout payment flow — raw card data must never be
 * collected by this app (PCI SAQ-A). VITE_STRIPE_PUBLISHABLE_KEY is the Stripe publishable
 * (client-side) key, pk_.... Never commit a real value; use a Stripe test key for local/dev.
 */
export const STRIPE_PUBLISHABLE_KEY: string = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? ''
