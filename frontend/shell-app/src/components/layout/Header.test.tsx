import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { Header } from './Header'
import { lightTheme } from '../../theme'
import { createAuth0Mock, authenticatedAuth0Mock } from '../../test/mocks/auth0'
import { useUserPreferencesStore } from '../../stores/userPreferencesStore'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

const mockMatchMedia = (matches = false) =>
  vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

const renderHeader = (cartItemCount = 0) =>
  render(
    <BrowserRouter>
      <ThemeProvider theme={lightTheme}>
        <Header cartItemCount={cartItemCount} onMenuClick={vi.fn()} />
      </ThemeProvider>
    </BrowserRouter>
  )

describe('Header', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseAuth0.mockReturnValue(createAuth0Mock() as ReturnType<typeof useAuth0>)
    useUserPreferencesStore.getState().resetPreferences()
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: mockMatchMedia(false),
    })
  })

  afterEach(() => {
    document.documentElement.classList.remove('light', 'dark')
  })

  it('should render the logo/brand', () => {
    renderHeader()
    expect(screen.getByText('E-Commerce')).toBeInTheDocument()
  })

  it('should render navigation links on desktop', () => {
    renderHeader()
    expect(screen.getByRole('link', { name: /home/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /products/i })).toBeInTheDocument()
  })

  it('should render search input', () => {
    renderHeader()
    expect(screen.getByPlaceholderText(/search products/i)).toBeInTheDocument()
  })

  it('should render cart icon with badge showing item count', () => {
    renderHeader(5)
    expect(screen.getByTestId('cart-badge')).toHaveTextContent('5')
  })

  it('should render cart icon with invisible badge when cart is empty', () => {
    renderHeader(0)
    const badge = screen.getByTestId('cart-badge')
    expect(badge).toBeInTheDocument()
  })

  it('should render login button when not authenticated', () => {
    renderHeader()
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
  })

  it('should render user menu when authenticated', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock as ReturnType<typeof useAuth0>)
    renderHeader()
    expect(screen.getByTestId('user-menu-button')).toBeInTheDocument()
  })

  it('should call onMenuClick when menu button is clicked on mobile', () => {
    const onMenuClick = vi.fn()
    render(
      <BrowserRouter>
        <ThemeProvider theme={lightTheme}>
          <Header cartItemCount={0} onMenuClick={onMenuClick} />
        </ThemeProvider>
      </BrowserRouter>
    )
    const menuButton = screen.getByTestId('mobile-menu-button')
    fireEvent.click(menuButton)
    expect(onMenuClick).toHaveBeenCalledTimes(1)
  })

  describe('theme toggle button', () => {
    it('should render the theme toggle button', () => {
      renderHeader()
      expect(screen.getByTestId('theme-toggle-button')).toBeInTheDocument()
    })

    it('should have aria-label indicating current mode when in light mode', () => {
      useUserPreferencesStore.getState().setTheme('light')
      renderHeader()
      const toggle = screen.getByTestId('theme-toggle-button')
      expect(toggle).toHaveAttribute('aria-label', 'Switch to dark mode')
    })

    it('should have aria-label indicating current mode when in dark mode', () => {
      useUserPreferencesStore.getState().setTheme('dark')
      renderHeader()
      const toggle = screen.getByTestId('theme-toggle-button')
      expect(toggle).toHaveAttribute('aria-label', 'Switch to light mode')
    })

    it('should toggle theme on click', () => {
      useUserPreferencesStore.getState().setTheme('light')
      renderHeader()
      const toggle = screen.getByTestId('theme-toggle-button')
      fireEvent.click(toggle)
      expect(useUserPreferencesStore.getState().theme).toBe('dark')
    })

    it('should toggle theme from dark to light on click', () => {
      useUserPreferencesStore.getState().setTheme('dark')
      renderHeader()
      const toggle = screen.getByTestId('theme-toggle-button')
      fireEvent.click(toggle)
      expect(useUserPreferencesStore.getState().theme).toBe('light')
    })
  })

  it('should render the app header element', () => {
    renderHeader()
    expect(screen.getByTestId('app-header')).toBeInTheDocument()
  })
})
