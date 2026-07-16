import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import type { SavedPaymentMethod } from '../api/types'

vi.mock('../config/payments', () => ({
  STRIPE_PUBLISHABLE_KEY: 'pk_test_fake',
}))

const loadStripe = vi.fn((..._args: unknown[]) => Promise.resolve({}))
vi.mock('@stripe/stripe-js', () => ({
  loadStripe: (...args: unknown[]) => loadStripe(...args),
}))

let seenOptions: unknown
vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children, options }: { children: React.ReactNode; options: unknown }) => {
    seenOptions = options
    return <div data-testid="elements">{children}</div>
  },
}))

vi.mock('./StripePaymentForm', () => ({
  default: () => <div data-testid="stripe-payment-form" />,
}))

const listSavedMethods = vi.fn()
const confirmSavedMethodPayment = vi.fn()
vi.mock('../api/paymentMethodsService', () => ({
  listSavedMethods: (...args: unknown[]) => listSavedMethods(...args),
  confirmSavedMethodPayment: (...args: unknown[]) => confirmSavedMethodPayment(...args),
}))

// The saved-card path must never call Stripe.js — this stays unused-but-asserted to prove that.
const confirmPayment = vi.fn()
const fakeStripeInstance = { confirmPayment }

import StripeCheckout from './StripeCheckout'

const savedVisa: SavedPaymentMethod = {
  id: 1,
  userId: 'auth0|test-user',
  provider: 'stripe',
  providerId: 'pm_test_visa',
  last4: '4242',
  brand: 'visa',
  expMonth: 12,
  expYear: 2027,
  isDefault: true,
  createdAt: '2026-01-01T00:00:00.000Z',
}

const baseProps = {
  clientSecret: 'pi_test_123_secret_abc',
  onConfirmed: vi.fn(),
  userId: 'auth0|test-user',
}

describe('StripeCheckout — saved payment methods (authenticated)', () => {
  beforeEach(() => {
    loadStripe.mockResolvedValue(fakeStripeInstance)
    confirmPayment.mockReset()
    listSavedMethods.mockReset()
    confirmSavedMethodPayment.mockReset()
    baseProps.onConfirmed.mockReset()
  })

  it('should not fetch or show saved methods for a guest (no userId)', async () => {
    renderWithProviders(
      <StripeCheckout clientSecret={baseProps.clientSecret} onConfirmed={baseProps.onConfirmed} />
    )
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(listSavedMethods).not.toHaveBeenCalled()
    expect(seenOptions).toEqual({ clientSecret: baseProps.clientSecret })
  })

  it('should fall through silently to the new-card Elements flow when there are no saved methods', async () => {
    listSavedMethods.mockResolvedValue([])
    renderWithProviders(<StripeCheckout {...baseProps} />)

    await waitFor(() => expect(listSavedMethods).toHaveBeenCalledWith('auth0|test-user'))
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('should fall through silently to the new-card flow when the saved-methods fetch fails', async () => {
    listSavedMethods.mockRejectedValue(new Error('network error'))
    renderWithProviders(<StripeCheckout {...baseProps} />)

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/could not load/i))
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
  })

  it('should preselect the default saved method and confirm through the server (no Elements, no Stripe.js confirm)', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    confirmSavedMethodPayment.mockResolvedValue({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: baseProps.clientSecret,
      status: 'COMPLETED',
    })

    renderWithProviders(<StripeCheckout {...baseProps} />)

    const radio = await screen.findByRole('radio', { name: /visa.*4242.*12\/2027/i })
    await waitFor(() => expect(radio).toBeChecked())

    expect(screen.queryByTestId('elements')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() =>
      expect(confirmSavedMethodPayment).toHaveBeenCalledWith('pi_test_123', 'pm_test_visa')
    )
    expect(confirmPayment).not.toHaveBeenCalled()
    await waitFor(() => expect(baseProps.onConfirmed).toHaveBeenCalledWith('pi_test_123'))
  })

  it('should switch to the new-card Elements flow when "Use a new card" is selected', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    renderWithProviders(<StripeCheckout {...baseProps} />)

    await screen.findByRole('radio', { name: /visa.*4242/i })
    await userEvent.click(screen.getByRole('radio', { name: /use a new card/i }))

    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /pay now/i })).not.toBeInTheDocument()
  })

  it('should surface a user-facing error and not confirm when the server rejects (e.g. 403 ownership check)', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    confirmSavedMethodPayment.mockRejectedValue({ response: { status: 403 } })

    renderWithProviders(<StripeCheckout {...baseProps} />)
    await screen.findByRole('radio', { name: /visa.*4242/i })

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(confirmPayment).not.toHaveBeenCalled()
    expect(baseProps.onConfirmed).not.toHaveBeenCalled()
  })

  it('should surface a user-facing error and not confirm when the server reports a non-COMPLETED status', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    confirmSavedMethodPayment.mockResolvedValue({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: baseProps.clientSecret,
      status: 'FAILED',
    })

    renderWithProviders(<StripeCheckout {...baseProps} />)
    await screen.findByRole('radio', { name: /visa.*4242/i })

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/could not be completed/i))
    expect(baseProps.onConfirmed).not.toHaveBeenCalled()
  })
})
