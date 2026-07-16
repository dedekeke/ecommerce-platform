import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import GuestAuthGate from './GuestAuthGate'

describe('GuestAuthGate', () => {
  it('should not call back when the email is invalid', async () => {
    const onContinue = vi.fn()
    render(<GuestAuthGate onContinueAsGuest={onContinue} />)

    await userEvent.type(screen.getByLabelText(/email address/i), 'not-an-email')
    await userEvent.click(screen.getByRole('button', { name: /continue as guest/i }))

    expect(onContinue).not.toHaveBeenCalled()
    expect(screen.getByText(/valid email/i)).toBeInTheDocument()
  })

  it('should call back with the trimmed email on a valid submit', async () => {
    const onContinue = vi.fn()
    render(<GuestAuthGate onContinueAsGuest={onContinue} />)

    await userEvent.type(screen.getByLabelText(/email address/i), '  buyer@example.com  ')
    await userEvent.click(screen.getByRole('button', { name: /continue as guest/i }))

    expect(onContinue).toHaveBeenCalledWith('buyer@example.com')
  })

  it('should offer a sign-in hint as an alternative', () => {
    render(<GuestAuthGate onContinueAsGuest={vi.fn()} />)
    expect(screen.getByRole('note')).toHaveTextContent(/sign in/i)
  })
})
