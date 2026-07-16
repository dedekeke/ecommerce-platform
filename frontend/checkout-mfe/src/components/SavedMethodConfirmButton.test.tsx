import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Stripe } from '@stripe/stripe-js'
import { renderWithProviders } from '../test/renderWithProviders'
import SavedMethodConfirmButton from './SavedMethodConfirmButton'

const confirmPayment = vi.fn()
const fakeStripe = { confirmPayment } as unknown as Stripe

const props = {
  clientSecret: 'pi_test_123_secret_abc',
  providerId: 'pm_test_visa',
  onConfirmed: vi.fn(),
}

describe('SavedMethodConfirmButton', () => {
  beforeEach(() => {
    confirmPayment.mockReset()
    props.onConfirmed.mockReset()
  })

  it('should confirm the PaymentIntent with the saved payment_method id, without Elements', async () => {
    confirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_saved_1', status: 'succeeded' } })
    renderWithProviders(
      <SavedMethodConfirmButton {...props} stripePromise={Promise.resolve(fakeStripe)} />
    )

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() =>
      expect(confirmPayment).toHaveBeenCalledWith({
        clientSecret: props.clientSecret,
        confirmParams: { payment_method: props.providerId },
        redirect: 'if_required',
      })
    )
    await waitFor(() => expect(props.onConfirmed).toHaveBeenCalledWith('pi_saved_1'))
  })

  it('should show an error alert when Stripe returns a confirmation error', async () => {
    confirmPayment.mockResolvedValue({ error: { message: 'Your card was declined.' } })
    renderWithProviders(
      <SavedMethodConfirmButton
        {...props}
        stripePromise={Promise.resolve(fakeStripe)}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/declined/i))
    expect(props.onConfirmed).not.toHaveBeenCalled()
  })

  it('should show an error when the intent does not succeed and no error is returned', async () => {
    confirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_x', status: 'requires_action' } })
    renderWithProviders(
      <SavedMethodConfirmButton
        {...props}
        stripePromise={Promise.resolve(fakeStripe)}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/not completed/i))
    expect(props.onConfirmed).not.toHaveBeenCalled()
  })

  it('should show a configuration error when the Stripe instance resolves to null', async () => {
    renderWithProviders(<SavedMethodConfirmButton {...props} stripePromise={Promise.resolve(null)} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/not configured/i))
    expect(confirmPayment).not.toHaveBeenCalled()
  })
})
