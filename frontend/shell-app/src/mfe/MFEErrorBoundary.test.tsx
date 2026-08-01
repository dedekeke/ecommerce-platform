import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MFEErrorBoundary } from './MFEErrorBoundary'

vi.mock('./registry', () => ({
  getMFEConfig: vi.fn((name) => ({
    name,
    displayName: name === 'productCatalog' ? 'Product Catalog' : 'Unknown',
    remoteUrl: `http://localhost:5001/assets/remoteEntry.js`,
    exposedModule: './ProductCatalog',
    fallbackSkeleton: 'productList',
    requiresAuth: false,
  })),
}))

const ErrorThrowingComponent = () => {
  throw new Error('Test error from MFE')
}

describe('MFEErrorBoundary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Normal Operation', () => {
    it('should render children when no error occurs', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <div data-testid="child-content">Child Content</div>
        </MFEErrorBoundary>
      )

      expect(screen.getByTestId('child-content')).toBeInTheDocument()
    })

    it('should not show error UI when children render successfully', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <div>Normal Content</div>
        </MFEErrorBoundary>
      )

      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('Error Handling', () => {
    it('should catch errors from children and display error UI', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByText(/something went wrong/i)).toBeInTheDocument()
    })

    it('should display MFE name in error message', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByText(/Product Catalog/)).toBeInTheDocument()
    })

    it('should display error message', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByText(/Test error from MFE/)).toBeInTheDocument()
    })

    it('should show retry button', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
    })

    it('should log error to console', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(console.error).toHaveBeenCalled()
    })
  })

  describe('Custom Fallback', () => {
    it('should render custom fallback when provided', () => {
      render(
        <MFEErrorBoundary
          mfeName="productCatalog"
          fallback={<div data-testid="custom-error">Custom Error UI</div>}
        >
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByTestId('custom-error')).toBeInTheDocument()
    })

    it('should not show default error UI when custom fallback is provided', () => {
      render(
        <MFEErrorBoundary
          mfeName="productCatalog"
          fallback={<div>Custom Error</div>}
        >
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
    })
  })

  describe('Retry Functionality', () => {
    it('should call onRetry callback when retry button is clicked', async () => {
      const user = userEvent.setup()
      const onRetryMock = vi.fn()

      render(
        <MFEErrorBoundary mfeName="productCatalog" onRetry={onRetryMock}>
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      await user.click(screen.getByRole('button', { name: /retry/i }))

      expect(onRetryMock).toHaveBeenCalledTimes(1)
    })

    it('should reset error state when retry is clicked', async () => {
      const user = userEvent.setup()
      let shouldThrow = true

      const ToggleError = () => {
        if (shouldThrow) {
          throw new Error('Error!')
        }
        return <div data-testid="success">Success</div>
      }

      const { rerender } = render(
        <MFEErrorBoundary mfeName="productCatalog" key="initial">
          <ToggleError />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('alert')).toBeInTheDocument()

      shouldThrow = false
      await user.click(screen.getByRole('button', { name: /retry/i }))

      rerender(
        <MFEErrorBoundary mfeName="productCatalog" key="retry">
          <ToggleError />
        </MFEErrorBoundary>
      )

      expect(screen.getByTestId('success')).toBeInTheDocument()
    })

    it('should track retry count', async () => {
      const user = userEvent.setup()
      const onRetryMock = vi.fn()

      render(
        <MFEErrorBoundary mfeName="productCatalog" onRetry={onRetryMock} maxRetries={3}>
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      await user.click(screen.getByRole('button', { name: /retry/i }))

      expect(onRetryMock).toHaveBeenCalledTimes(1)
    })

    it('should disable retry button when max retries reached', async () => {
      const user = userEvent.setup()

      render(
        <MFEErrorBoundary mfeName="productCatalog" maxRetries={2}>
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      let retryButton = screen.getByRole('button', { name: /retry/i })

      await user.click(retryButton)
      retryButton = screen.getByRole('button', { name: /retry/i })
      await user.click(retryButton)

      retryButton = screen.getByRole('button', { name: /max retries reached/i })
      expect(retryButton).toBeDisabled()
    })

    it('should show retry count when approaching max', async () => {
      const user = userEvent.setup()

      render(
        <MFEErrorBoundary mfeName="productCatalog" maxRetries={3}>
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      await user.click(screen.getByRole('button', { name: /retry/i }))

      expect(screen.getByText(/1.*of.*3/i)).toBeInTheDocument()
    })
  })

  describe('Go Home Button', () => {
    it('should show go home button', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('button', { name: /home/i })).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have accessible error state with role="alert"', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('alert')).toBeInTheDocument()
    })

    it('should have descriptive button labels', () => {
      render(
        <MFEErrorBoundary mfeName="productCatalog">
          <ErrorThrowingComponent />
        </MFEErrorBoundary>
      )

      expect(screen.getByRole('button', { name: /retry/i })).toHaveAccessibleName()
      expect(screen.getByRole('button', { name: /home/i })).toHaveAccessibleName()
    })
  })
})
