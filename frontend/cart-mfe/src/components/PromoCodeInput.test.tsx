import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import PromoCodeInput from './PromoCodeInput'

const baseProps = {
  appliedCode: null,
  isApplying: false,
  error: null,
  onApply: vi.fn(),
  onRemove: vi.fn(),
}

describe('PromoCodeInput', () => {
  it('should render a promo code input and an Apply button', () => {
    renderWithProviders(<PromoCodeInput {...baseProps} />)
    expect(screen.getByRole('textbox', { name: /promo code/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /apply/i })).toBeInTheDocument()
  })

  it('should disable the Apply button when the input is empty', () => {
    renderWithProviders(<PromoCodeInput {...baseProps} />)
    expect(screen.getByRole('button', { name: /apply/i })).toBeDisabled()
  })

  it('should call onApply with the entered code when Apply is clicked', async () => {
    const onApply = vi.fn()
    renderWithProviders(<PromoCodeInput {...baseProps} onApply={onApply} />)

    await userEvent.type(screen.getByRole('textbox', { name: /promo code/i }), 'save10')
    await userEvent.click(screen.getByRole('button', { name: /apply/i }))

    expect(onApply).toHaveBeenCalledWith('SAVE10')
  })

  it('should call onApply when Enter is pressed in the input', async () => {
    const onApply = vi.fn()
    renderWithProviders(<PromoCodeInput {...baseProps} onApply={onApply} />)

    await userEvent.type(screen.getByRole('textbox', { name: /promo code/i }), 'SAVE10{Enter}')

    expect(onApply).toHaveBeenCalledWith('SAVE10')
  })

  it('should not call onApply while a request is in flight', () => {
    const onApply = vi.fn()
    renderWithProviders(<PromoCodeInput {...baseProps} isApplying onApply={onApply} />)
    expect(screen.getByRole('button', { name: /applying/i })).toBeDisabled()
  })

  it('should display the inline error message when provided', () => {
    renderWithProviders(<PromoCodeInput {...baseProps} error="This promo code is not valid" />)
    expect(screen.getByRole('alert')).toHaveTextContent('This promo code is not valid')
    expect(screen.getByRole('textbox', { name: /promo code/i })).toBeInvalid()
  })

  it('should render an applied-code chip and a Remove button when a code is applied', () => {
    renderWithProviders(<PromoCodeInput {...baseProps} appliedCode="SAVE10" />)
    expect(screen.getByText(/SAVE10/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /remove/i })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /promo code/i })).not.toBeInTheDocument()
  })

  it('should call onRemove when the Remove button is clicked', async () => {
    const onRemove = vi.fn()
    renderWithProviders(<PromoCodeInput {...baseProps} appliedCode="SAVE10" onRemove={onRemove} />)
    await userEvent.click(screen.getByRole('button', { name: /remove/i }))
    expect(onRemove).toHaveBeenCalledOnce()
  })
})
