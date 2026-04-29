import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import PaymentMethodForm from './PaymentMethodForm'

describe('PaymentMethodForm', () => {
  it('should render Card and PayPal radio options', () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    expect(screen.getByRole('radio', { name: /card/i })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /paypal/i })).toBeInTheDocument()
  })

  it('should default to Card selected', () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    expect(screen.getByRole('radio', { name: /card/i })).toBeChecked()
  })

  it('should show card fields when Card is selected', () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    expect(screen.getByLabelText(/card number/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/expiry/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/cvv/i)).toBeInTheDocument()
  })

  it('should hide card fields when PayPal is selected', async () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    await userEvent.click(screen.getByRole('radio', { name: /paypal/i }))
    expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/expiry/i)).not.toBeInTheDocument()
  })

  it('should call onPaymentMethodReady with a mock_paypal id when PayPal is selected', async () => {
    const onPaymentMethodReady = vi.fn()
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={onPaymentMethodReady} />)
    await userEvent.click(screen.getByRole('radio', { name: /paypal/i }))
    await waitFor(() => {
      expect(onPaymentMethodReady).toHaveBeenCalledWith(
        expect.stringMatching(/^mock_paypal_/)
      )
    })
  })

  it('should not call onPaymentMethodReady while card fields are invalid', async () => {
    const onPaymentMethodReady = vi.fn()
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={onPaymentMethodReady} />)
    expect(onPaymentMethodReady).not.toHaveBeenCalled()
  })

  it('should call onPaymentMethodReady with mock_card_ id when card fields are valid', async () => {
    const onPaymentMethodReady = vi.fn()
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={onPaymentMethodReady} />)
    await userEvent.type(screen.getByLabelText(/card number/i), '4111111111111111')
    await userEvent.type(screen.getByLabelText(/expiry/i), '12/28')
    await userEvent.type(screen.getByLabelText(/cvv/i), '123')
    await waitFor(() => {
      expect(onPaymentMethodReady).toHaveBeenCalledWith(
        expect.stringMatching(/^mock_card_/)
      )
    })
  })

  it('should show validation error for empty card number on blur', async () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    await userEvent.click(screen.getByLabelText(/card number/i))
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/card number is required/i)).toBeInTheDocument()
    })
  })

  it('should show validation error for invalid card number format', async () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/card number/i), '123')
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/must be 16 digits/i)).toBeInTheDocument()
    })
  })

  it('should show validation error for invalid expiry format', async () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/expiry/i), '13/99')
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/invalid expiry/i)).toBeInTheDocument()
    })
  })

  it('should show validation error for invalid cvv', async () => {
    renderWithProviders(<PaymentMethodForm onPaymentMethodReady={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/cvv/i), '12')
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/cvv must be 3.4 digits/i)).toBeInTheDocument()
    })
  })
})
