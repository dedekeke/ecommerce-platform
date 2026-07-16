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
vi.mock('../api/paymentMethodsService', () => ({
  listSavedMethods: (...args: unknown[]) => listSavedMethods(...args),
}))

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

  it('should preselect the default saved method and confirm with its payment_method id (no Elements)', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    confirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_saved_1', status: 'succeeded' } })

    renderWithProviders(<StripeCheckout {...baseProps} />)

    const radio = await screen.findByRole('radio', { name: /visa.*4242.*12\/2027/i })
    await waitFor(() => expect(radio).toBeChecked())

    expect(screen.queryByTestId('elements')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() =>
      expect(confirmPayment).toHaveBeenCalledWith({
        clientSecret: baseProps.clientSecret,
        confirmParams: { payment_method: 'pm_test_visa' },
        redirect: 'if_required',
      })
    )
    await waitFor(() => expect(baseProps.onConfirmed).toHaveBeenCalledWith('pi_saved_1'))
  })

  it('should switch to the new-card Elements flow when "Use a new card" is selected', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    renderWithProviders(<StripeCheckout {...baseProps} />)

    await screen.findByRole('radio', { name: /visa.*4242/i })
    await userEvent.click(screen.getByRole('radio', { name: /use a new card/i }))

    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /pay now/i })).not.toBeInTheDocument()
  })

  it('should surface a user-facing error when confirming a saved method fails', async () => {
    listSavedMethods.mockResolvedValue([savedVisa])
    confirmPayment.mockResolvedValue({ error: { message: 'Your card was declined.' } })

    renderWithProviders(<StripeCheckout {...baseProps} />)
    await screen.findByRole('radio', { name: /visa.*4242/i })

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/declined/i))
    expect(baseProps.onConfirmed).not.toHaveBeenCalled()
  })
})
