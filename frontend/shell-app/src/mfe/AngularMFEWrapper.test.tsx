import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { AngularMFEWrapper } from './AngularMFEWrapper'

const mockLoadRemoteModule = vi.fn()

vi.mock('@angular-architects/native-federation-runtime', () => ({
  loadRemoteModule: (...args: unknown[]) => mockLoadRemoteModule(...args),
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
  })),
}))

describe('AngularMFEWrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should show loading state while the Angular MFE bootstrap is being loaded', () => {
    mockLoadRemoteModule.mockReturnValue(new Promise(() => {}))

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    expect(screen.getByTestId('angular-mfe-loading')).toBeInTheDocument()
    expect(screen.getByText(/loading user dashboard/i)).toBeInTheDocument()
  })

  it('should render a container div after successful bootstrap load', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(undefined)
    mockLoadRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mount-userDashboard')).toBeInTheDocument()
    })
  })

  it('should call the bootstrap function from the remote module with the mount element id', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(undefined)
    mockLoadRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(mockBootstrap).toHaveBeenCalledWith('angular-mount-userDashboard')
    })
  })

  it('should call loadRemoteModule with the correct remoteName and exposedModule', async () => {
    const mockBootstrap = vi.fn().mockResolvedValue(undefined)
    mockLoadRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="adminDashboard" />)

    await waitFor(() => {
      expect(mockLoadRemoteModule).toHaveBeenCalledWith({
        remoteName: 'adminDashboard',
        exposedModule: './AdminDashboard',
      })
    })
  })

  it('should show an error state when bootstrap fails', async () => {
    mockLoadRemoteModule.mockRejectedValue(new Error('bootstrap failed'))

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
      expect(screen.getByText(/failed to load user dashboard/i)).toBeInTheDocument()
    })
  })

  it('should show an error when bootstrap function throws', async () => {
    const mockBootstrap = vi.fn().mockRejectedValue(new Error('Angular init error'))
    mockLoadRemoteModule.mockResolvedValue({ bootstrap: mockBootstrap })

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
    })
  })

  it('should show error when remote module has no bootstrap export', async () => {
    mockLoadRemoteModule.mockResolvedValue({})

    render(<AngularMFEWrapper mfeName="userDashboard" />)

    await waitFor(() => {
      expect(screen.getByTestId('angular-mfe-error')).toBeInTheDocument()
      expect(screen.getByText(/no bootstrap function/i)).toBeInTheDocument()
    })
  })
})
