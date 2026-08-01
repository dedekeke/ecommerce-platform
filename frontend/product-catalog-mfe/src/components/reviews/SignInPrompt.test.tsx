import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { SignInPrompt } from './SignInPrompt'

describe('SignInPrompt', () => {
  it('should render a sign-in message', () => {
    renderWithProviders(<SignInPrompt />)
    expect(screen.getByRole('alert')).toHaveTextContent(/sign in to write a review/i)
  })
})
