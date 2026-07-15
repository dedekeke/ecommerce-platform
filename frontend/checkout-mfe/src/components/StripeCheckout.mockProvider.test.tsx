import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'

// No publishable key configured. Verifies loadStripe is NEVER called with an empty key, and
// that there is no raw-card-form fallback (PCI SAQ-A) — just a configuration error.
vi.mock('../config/payments', () => ({
  STRIPE_PUBLISHABLE_KEY: '',
}))

const loadStripe = vi.fn((..._args: unknown[]) => Promise.resolve({}))
vi.mock('@stripe/stripe-js', () => ({
  loadStripe: (...args: unknown[]) => loadStripe(...args),
}))

vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('./StripePaymentForm', () => ({ default: () => <div /> }))

import StripeCheckout from './StripeCheckout'

const props = {
  clientSecret: 'pi_test_123_secret_abc',
  onConfirmed: vi.fn(),
}

describe('StripeCheckout — missing publishable key', () => {
  it('should not call loadStripe and should render a configuration error', () => {
    renderWithProviders(<StripeCheckout {...props} />)
    expect(loadStripe).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(/payment is not configured/i)
  })
})
