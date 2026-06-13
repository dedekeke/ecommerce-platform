import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AngularMFEWrapper } from './AngularMFEWrapper'

// Mock the native-federation runtime (fallback path for non-JSON remotes)
const mockLoadRemoteModule = vi.fn()

vi.mock('@angular-architects/native-federation-runtime', () => ({
  loadRemoteModule: (...args: unknown[]) => mockLoadRemoteModule(...args),
}))

// Mock the federation bridge (primary path — used when remoteUrl ends with .json)
const mockLoadAngularRemoteModule = vi.fn()

vi.mock('./angularFederationBridge', () => ({
  loadAngularRemoteModule: (...args: unknown[]) => mockLoadAngularRemoteModule(...args),
}))

vi.mock('./registry', () => ({
  getMFEConfig: vi.fn((name: string) => ({
    name,
    displayName: name === 'userDashboard' ? 'User Dashboard' : 'Admin Dashboard',
    remoteUrl: `http://localhost:5004/remoteEntry.json`,
    exposedModule: name === 'userDashboard' ? './UserDashboard' : './AdminDashboard',
    fallbackSkeleton: 'profile',
    requiresAuth: true,
    runtime: 'native',
    basePath: name === 'userDashboard' ? '/profile' : '/admin',
  })),
}))

describe('AngularMFEWrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Registry mock uses remoteUrl ending in .json, so the bridge (loadAngularRemoteModule)
  // is the active loader in all these tests.

  it('should show loading state while the Angular MFE bootstrap is being loaded', () => {
    mockLoadAngularRemoteModule.mockReturnValue(new Promise(() => {}))

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    expect(screen.getByTestId('angular-mfe-loading')).toBeInTheDocument()
    expect(screen.getByText(/loading user dashboard/i)).toBeInTheDocument()
  })

  it('should render a container div after successful bootstrap load', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(vi.fn())
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mount-userDashboard')).toBeInTheDocument()
    })
  })

  it('should call the bootstrap function from the remote module with the mount element id', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(vi.fn())
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(mockBootstrap).toHaveBeenCalledWith('angular-mount-userDashboard')
    })
  })

  it('should call the bridge loader with the correct remoteUrl and exposedModule', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(vi.fn())
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="adminDashboard" />)

    await waitFor(() => {
      expect(mockLoadAngularRemoteModule).toHaveBeenCalledWith(
        'http://localhost:5004/remoteEntry.json',
        './AdminDashboard'
      )
    })
  })

  it('should call the destroy handle returned by bootstrap when unmounting', async () => {
    const mockDestroy = vi.fn()
    const mockBootstrap = vi.fn().mockResolvedValue(mockDestroy)
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    const { unmount } = render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(mockBootstrap).toHaveBeenCalled()
    })

    unmount()

    expect(mockDestroy).toHaveBeenCalledTimes(1)
  })

  it('should not call the destroy handle when unmounted before bootstrap resolves', () => {
    const mockDestroy = vi.fn()
    // Never resolves — simulates slow network
    mockLoadAngularRemoteModule.mockReturnValue(
      new Promise<{ bootstrap: () => Promise<() => void> }>(() => {})
    )

    const { unmount } = render(<AngularMFEWrapper mfeName="userDashboard" />)
    unmount()

    expect(mockDestroy).not.toHaveBeenCalled()
  })

  it('should show an error state when bootstrap fails', async () => {
    mockLoadAngularRemoteModule.mockRejectedValue(new Error('bootstrap failed'))

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
      expect(screen.getByText(/failed to load user dashboard/i)).toBeInTheDocument()
    })
  })

  it('should show an error when bootstrap function throws', async () => {
    const mockBootstrap = vi.fn().mockRejectedValue(new Error('Angular init error'))
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
    })
  })

  it('should show error when remote module has no bootstrap export', async () => {
    mockLoadAngularRemoteModule.mockResolvedValue({})

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
      expect(screen.getByText(/no bootstrap function/i)).toBeInTheDocument()
    })
  })

  it('should retry loading on Retry button click', async () => {
    const user = userEvent.setup()
    mockLoadAngularRemoteModule.mockRejectedValue(new Error('network error'))

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
    })

    const mockBootstrap = vi.fn().mockResolvedValue(vi.fn())
    mockLoadAngularRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    await user.click(screen.getByRole('button', { name: /retry/i }))

    await waitFor(() => {
      expect(mockLoadAngularRemoteModule).toHaveBeenCalledTimes(2)
    })
  })

  it('should use native-federation runtime when remoteUrl does not end with .json', async () => {
    const { getMFEConfig } = await import('./registry')
    vi.mocked(getMFEConfig).mockReturnValueOnce({
      name: 'userDashboard',
      displayName: 'User Dashboard',
      remoteUrl: 'http://localhost:5004/assets/remoteEntry.js',
      exposedModule: './UserDashboard',
      fallbackSkeleton: 'profile',
      requiresAuth: true,
      runtime: 'native',
      basePath: '/profile',
    })
    const mockBootstrap = vi.fn().mockResolvedValue(vi.fn())
    mockLoadRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(mockLoadRemoteModule).toHaveBeenCalledWith({
        remoteName: 'userDashboard',
        exposedModule: './UserDashboard',
      })
    })
  })
})
