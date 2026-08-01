import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import CheckoutStepper from './CheckoutStepper'

const steps = ['Shipping', 'Payment', 'Review']

describe('CheckoutStepper', () => {
  it('should render all step labels', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={0}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    steps.forEach((label) => {
      expect(screen.getByText(label)).toBeInTheDocument()
    })
  })

  it('should disable Back button on first step', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={0}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    expect(screen.getByRole('button', { name: /back/i })).toBeDisabled()
  })

  it('should enable Back button on steps after first', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={1}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    expect(screen.getByRole('button', { name: /back/i })).toBeEnabled()
  })

  it('should disable Next button when canProceed is false', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={0}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={false}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('should show "Place Order" instead of "Next" on the last step', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={2}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={true}
        isSubmitting={false}
      />
    )
    expect(screen.getByRole('button', { name: /place order/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^next$/i })).not.toBeInTheDocument()
  })

  it('should call onNext when Next is clicked', async () => {
    const onNext = vi.fn()
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={0}
        onBack={vi.fn()}
        onNext={onNext}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /next/i }))
    expect(onNext).toHaveBeenCalledOnce()
  })

  it('should call onBack when Back is clicked', async () => {
    const onBack = vi.fn()
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={1}
        onBack={onBack}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /back/i }))
    expect(onBack).toHaveBeenCalledOnce()
  })

  it('should disable Next button and show loading state when isSubmitting', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={2}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={true}
        isSubmitting={true}
      />
    )
    expect(screen.getByRole('button', { name: /placing order/i })).toBeDisabled()
  })

  it('should show a custom nextLabel on a non-final step when provided', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={1}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={true}
        isLastStep={false}
        isSubmitting={false}
        nextLabel="Continue to payment"
      />
    )
    expect(screen.getByRole('button', { name: /continue to payment/i })).toBeInTheDocument()
  })

  it('should hide the Back/Next action bar when hideActions is true', () => {
    renderWithProviders(
      <CheckoutStepper
        steps={steps}
        activeStep={2}
        onBack={vi.fn()}
        onNext={vi.fn()}
        canProceed={false}
        isLastStep={true}
        isSubmitting={false}
        hideActions
      />
    )
    expect(screen.queryByRole('button', { name: /back/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /place order/i })).not.toBeInTheDocument()
    steps.forEach((label) => {
      expect(screen.getByText(label)).toBeInTheDocument()
    })
  })
})
