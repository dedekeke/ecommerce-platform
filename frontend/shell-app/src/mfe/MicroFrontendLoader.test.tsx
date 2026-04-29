import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MicroFrontendLoader } from './MicroFrontendLoader'

const MockComponent = () => <div data-testid="mock-mfe">Mock MFE Content</div>

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

const mockLoadRemote = vi.fn()
const mockClearModuleCache = vi.fn()

vi.mock('./moduleLoader', () => ({
  loadRemoteModule: () => mockLoadRemote(),
  clearModuleCache: (...args: unknown[]) => mockClearModuleCache(...args),
}))

describe('MicroFrontendLoader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('Loading State', () => {
    it('should show loading skeleton while MFE is loading', async () => {
      mockLoadRemote.mockReturnValue(new Promise(() => {}))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      expect(screen.getByTestId('mfe-loading-skeleton')).toBeInTheDocument()
    })

    it('should show custom fallback if provided', async () => {
      mockLoadRemote.mockReturnValue(new Promise(() => {}))

      render(
        <MicroFrontendLoader
          mfeName="productCatalog"
          fallback={<div data-testid="custom-fallback">Custom Loading...</div>}
        />
      )

      expect(screen.getByTestId('custom-fallback')).toBeInTheDocument()
    })

    it('should display correct loading message', async () => {
      mockLoadRemote.mockReturnValue(new Promise(() => {}))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      expect(screen.getByText(/loading product catalog/i)).toBeInTheDocument()
    })
  })

  describe('Success State', () => {
    it('should render MFE component after successful load', async () => {
      mockLoadRemote.mockResolvedValue({ default: MockComponent })

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByTestId('mock-mfe')).toBeInTheDocument()
      })
    })

    it('should call onLoad callback when MFE loads successfully', async () => {
      mockLoadRemote.mockResolvedValue({ default: MockComponent })
      const onLoadMock = vi.fn()

      render(<MicroFrontendLoader mfeName="productCatalog" onLoad={onLoadMock} />)

      await waitFor(() => {
        expect(onLoadMock).toHaveBeenCalled()
      })
    })

    it('should hide loading skeleton after load', async () => {
      mockLoadRemote.mockResolvedValue({ default: MockComponent })

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.queryByTestId('mfe-loading-skeleton')).not.toBeInTheDocument()
      })
    })
  })

  describe('Error State', () => {
    it('should show error UI when MFE fails to load', async () => {
      mockLoadRemote.mockRejectedValue(new Error('Failed to load module'))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument()
        expect(screen.getByText(/Failed to load Product Catalog/)).toBeInTheDocument()
      })
    })

    it('should call onError callback when MFE fails to load', async () => {
      const error = new Error('Network error')
      mockLoadRemote.mockRejectedValue(error)
      const onErrorMock = vi.fn()

      render(<MicroFrontendLoader mfeName="productCatalog" onError={onErrorMock} />)

      await waitFor(() => {
        expect(onErrorMock).toHaveBeenCalledWith(expect.any(Error))
      })
    })

    it('should show retry button on error', async () => {
      mockLoadRemote.mockRejectedValue(new Error('Failed'))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
      })
    })

    it('should retry loading when retry button is clicked', async () => {
      const user = userEvent.setup()
      mockLoadRemote
        .mockRejectedValueOnce(new Error('First attempt failed'))
        .mockResolvedValueOnce({ default: MockComponent })

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument()
      })

      const retryButton = screen.getByRole('button', { name: /retry/i })
      await user.click(retryButton)

      await waitFor(
        () => {
          expect(screen.getByTestId('mock-mfe')).toBeInTheDocument()
        },
        { timeout: 3000 }
      )
    })

    it('should display MFE name in error message', async () => {
      mockLoadRemote.mockRejectedValue(new Error('Failed'))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByText(/product catalog/i)).toBeInTheDocument()
      })
    })
  })

  describe('Component Props', () => {
    it('should pass componentProps to loaded MFE', async () => {
      const PropsComponent = ({ testProp }: { testProp: string }) => (
        <div data-testid="props-mfe">{testProp}</div>
      )
      mockLoadRemote.mockResolvedValue({ default: PropsComponent })

      render(
        <MicroFrontendLoader
          mfeName="productCatalog"
          componentProps={{ testProp: 'Hello MFE' }}
        />
      )

      await waitFor(() => {
        expect(screen.getByText('Hello MFE')).toBeInTheDocument()
      })
    })
  })

  describe('Accessibility', () => {
    it('should have accessible loading state', async () => {
      mockLoadRemote.mockReturnValue(new Promise(() => {}))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      const loadingElement = screen.getByTestId('mfe-loading-skeleton')
      expect(loadingElement).toHaveAttribute('aria-busy', 'true')
      expect(loadingElement).toHaveAttribute('aria-label')
    })

    it('should have accessible error state', async () => {
      mockLoadRemote.mockRejectedValue(new Error('Failed'))

      render(<MicroFrontendLoader mfeName="productCatalog" />)

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument()
      })
    })
  })
})
