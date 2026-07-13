import { describe, it, expect, vi } from 'vitest'

const loadStripe = vi.fn(() => Promise.resolve(null))

vi.mock('@stripe/stripe-js', () => ({ loadStripe }))
vi.mock('../config/payments', () => ({ STRIPE_PUBLISHABLE_KEY: 'pk_test_fake' }))

describe('StripeCheckout — SDK laziness', () => {
  it('should not call loadStripe at module import time', async () => {
    await import('./StripeCheckout')
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(loadStripe).not.toHaveBeenCalled()
  })
})
