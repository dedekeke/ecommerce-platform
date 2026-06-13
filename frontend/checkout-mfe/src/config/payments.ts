/**
 * Payment configuration sourced from Vite env vars.
 *
 * - VITE_PAYMENTS_PROVIDER: 'stripe' enables the real Stripe.js confirmation flow.
 *   Any other value (default) keeps the legacy mock card form so tests and local
 *   development run without a Stripe account.
 * - VITE_STRIPE_PUBLISHABLE_KEY: Stripe publishable key (pk_...). Never commit a real key.
 */
export const PAYMENTS_PROVIDER: string = import.meta.env.VITE_PAYMENTS_PROVIDER ?? 'mock'

export const STRIPE_PUBLISHABLE_KEY: string = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? ''

export const isStripeEnabled = (): boolean =>
  PAYMENTS_PROVIDER === 'stripe' && STRIPE_PUBLISHABLE_KEY.length > 0
