import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { MainLayout } from './MainLayout'
import theme from '../../theme'
import { createAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

const renderMainLayout = () => {
  return render(
    <BrowserRouter>
      <ThemeProvider theme={theme}>
        <MainLayout>
          <div data-testid="page-content">Page Content</div>
        </MainLayout>
      </ThemeProvider>
    </BrowserRouter>
  )
}

describe('MainLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseAuth0.mockReturnValue(createAuth0Mock() as unknown as ReturnType<typeof useAuth0>)
  })

  it('should render header', () => {
    renderMainLayout()
    expect(screen.getByText('E-Commerce')).toBeInTheDocument()
  })

  it('should render children content', () => {
    renderMainLayout()
    expect(screen.getByTestId('page-content')).toBeInTheDocument()
    expect(screen.getByText('Page Content')).toBeInTheDocument()
  })

  it('should render footer', () => {
    renderMainLayout()
    expect(screen.getByText(/© 2025 E-Commerce Platform/i)).toBeInTheDocument()
  })

  it('should have main content area', () => {
    renderMainLayout()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })
})
