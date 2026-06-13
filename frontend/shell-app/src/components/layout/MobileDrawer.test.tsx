import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { MobileDrawer } from './MobileDrawer'
import theme from '../../theme'
import { mockAuth0, authenticatedAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

const renderMobileDrawer = (open = true, onClose = vi.fn()) => {
  return render(
    <BrowserRouter>
      <ThemeProvider theme={theme}>
        <MobileDrawer open={open} onClose={onClose} />
      </ThemeProvider>
    </BrowserRouter>
  )
}

describe('MobileDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseAuth0.mockReturnValue(mockAuth0())
  })

  it('should render when open is true', () => {
    renderMobileDrawer(true)
    expect(screen.getByRole('presentation')).toBeInTheDocument()
  })

  it('should render navigation links', () => {
    renderMobileDrawer(true)
    expect(screen.getByText(/home/i)).toBeInTheDocument()
    expect(screen.getByText(/products/i)).toBeInTheDocument()
    expect(screen.getByText(/categories/i)).toBeInTheDocument()
  })

  it('should call onClose when close button is clicked', () => {
    const onClose = vi.fn()
    renderMobileDrawer(true, onClose)

    const closeButton = screen.getByTestId('drawer-close-button')
    fireEvent.click(closeButton)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('should render login button when not authenticated', () => {
    renderMobileDrawer(true)
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
  })

  it('should render user info when authenticated', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)
    renderMobileDrawer(true)
    expect(screen.getByText(/test user/i)).toBeInTheDocument()
  })

  it('should render cart link', () => {
    renderMobileDrawer(true)
    expect(screen.getByText(/cart/i)).toBeInTheDocument()
  })
})
