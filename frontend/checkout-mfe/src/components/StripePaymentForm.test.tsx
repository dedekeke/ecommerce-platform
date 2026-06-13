import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import StripePaymentForm from './StripePaymentForm'

const confirmPayment = vi.fn()
const useStripe = vi.fn()
const useElements = vi.fn()

vi.mock('@stripe/react-stripe-js', () => ({
  PaymentElement: () => <div data-testid="payment-element" />,
  useStripe: () => useStripe(),
  useElements: () => useElements(),
}))

describe('StripePaymentForm', () => {
  beforeEach(() => {
    confirmPayment.mockReset()
    useStripe.mockReturnValue({ confirmPayment })
    useElements.mockReturnValue({})
  })

  it('should render the Stripe PaymentElement', () => {
    renderWithProviders(<StripePaymentForm onConfirmed={vi.fn()} />)
    expect(screen.getByTestId('payment-element')).toBeInTheDocument()
  })

  it('should disable the pay button until Stripe is ready', () => {
    useStripe.mockReturnValue(null)
    useElements.mockReturnValue(null)
    renderWithProviders(<StripePaymentForm onConfirmed={vi.fn()} />)
    expect(screen.getByRole('button', { name: /pay now/i })).toBeDisabled()
  })

  it('should call onConfirmed with the payment intent id when confirmation succeeds', async () => {
    confirmPayment.mockResolvedValue({
      paymentIntent: { id: 'pi_test_123', status: 'succeeded' },
    })
    const onConfirmed = vi.fn()
    renderWithProviders(<StripePaymentForm onConfirmed={onConfirmed} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(onConfirmed).toHaveBeenCalledWith('pi_test_123'))
  })

  it('should show an error message when Stripe returns an error', async () => {
    confirmPayment.mockResolvedValue({ error: { message: 'Your card was declined.' } })
    const onConfirmed = vi.fn()
    renderWithProviders(<StripePaymentForm onConfirmed={onConfirmed} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/declined/i))
    expect(onConfirmed).not.toHaveBeenCalled()
  })

  it('should show an error when the intent is not succeeded and no error is returned', async () => {
    confirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_x', status: 'requires_action' } })
    const onConfirmed = vi.fn()
    renderWithProviders(<StripePaymentForm onConfirmed={onConfirmed} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/not completed/i))
    expect(onConfirmed).not.toHaveBeenCalled()
  })
})
