import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { ErrorBoundary } from './ErrorBoundary'
import theme from '../../theme'

const ThrowError = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('Test error')
  }
  return <div>No error</div>
}

const renderErrorBoundary = (shouldThrow = false) => {
  return render(
    <ThemeProvider theme={theme}>
      <ErrorBoundary>
        <ThrowError shouldThrow={shouldThrow} />
      </ErrorBoundary>
    </ThemeProvider>
  )
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should render children when no error occurs', () => {
    renderErrorBoundary(false)
    expect(screen.getByText('No error')).toBeInTheDocument()
  })

  it('should render error UI when error occurs', () => {
    renderErrorBoundary(true)
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument()
  })

  it('should display error message', () => {
    renderErrorBoundary(true)
    expect(screen.getByText(/test error/i)).toBeInTheDocument()
  })

  it('should render retry button', () => {
    renderErrorBoundary(true)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })

  it('should render go home button', () => {
    renderErrorBoundary(true)
    expect(screen.getByRole('button', { name: /go home/i })).toBeInTheDocument()
  })
})
