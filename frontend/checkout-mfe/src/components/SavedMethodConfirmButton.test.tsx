import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import SavedMethodConfirmButton from './SavedMethodConfirmButton'

const confirmSavedMethodPayment = vi.fn()
vi.mock('../api/paymentMethodsService', () => ({
  confirmSavedMethodPayment: (...args: unknown[]) => confirmSavedMethodPayment(...args),
}))

const props = {
  paymentIntentId: 'pi_test_123',
  paymentMethodId: 'pm_test_visa',
  onConfirmed: vi.fn(),
}

describe('SavedMethodConfirmButton', () => {
  beforeEach(() => {
    confirmSavedMethodPayment.mockReset()
    props.onConfirmed.mockReset()
  })

  it('should confirm the PaymentIntent through the server, not Stripe.js', async () => {
    confirmSavedMethodPayment.mockResolvedValue({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: 'pi_test_123_secret_abc',
      status: 'COMPLETED',
    })
    renderWithProviders(<SavedMethodConfirmButton {...props} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() =>
      expect(confirmSavedMethodPayment).toHaveBeenCalledWith('pi_test_123', 'pm_test_visa')
    )
    await waitFor(() => expect(props.onConfirmed).toHaveBeenCalledWith('pi_test_123'))
  })

  it('should show an error alert and not confirm when the server reports a non-COMPLETED status', async () => {
    confirmSavedMethodPayment.mockResolvedValue({
      paymentId: 2,
      paymentIntentId: 'pi_test_123',
      clientSecret: 'pi_test_123_secret_abc',
      status: 'FAILED',
    })
    renderWithProviders(<SavedMethodConfirmButton {...props} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/could not be completed/i)
    )
    expect(props.onConfirmed).not.toHaveBeenCalled()
  })

  it('should show a generic error and re-enable the button when the server request is rejected (e.g. 403)', async () => {
    confirmSavedMethodPayment.mockRejectedValue({
      response: { status: 403, data: { message: 'Forbidden' } },
    })
    renderWithProviders(<SavedMethodConfirmButton {...props} />)

    const button = screen.getByRole('button', { name: /pay now/i })
    await userEvent.click(button)

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(props.onConfirmed).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: /pay now/i })).toBeEnabled())
  })

  it('should show a generic error on a network failure', async () => {
    confirmSavedMethodPayment.mockRejectedValue(new Error('network error'))
    renderWithProviders(<SavedMethodConfirmButton {...props} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
    expect(props.onConfirmed).not.toHaveBeenCalled()
  })

  it('should show the accessible loading indicator while submitting', async () => {
    let resolvePayment: (value: unknown) => void = () => {}
    confirmSavedMethodPayment.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePayment = resolve
        })
    )
    renderWithProviders(<SavedMethodConfirmButton {...props} />)

    await userEvent.click(screen.getByRole('button', { name: /pay now/i }))

    expect(screen.getByLabelText(/processing payment/i)).toBeInTheDocument()

    resolvePayment({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: 'secret',
      status: 'COMPLETED',
    })
    await waitFor(() => expect(props.onConfirmed).toHaveBeenCalled())
  })
})
