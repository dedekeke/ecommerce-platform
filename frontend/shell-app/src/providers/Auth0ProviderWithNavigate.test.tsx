import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Auth0ProviderWithNavigate } from './Auth0ProviderWithNavigate'
import type { Auth0ProviderWithConfigOptions } from '@auth0/auth0-react'

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: vi.fn(({ children }) => <div data-testid="auth0-provider">{children}</div>),
}))

import { Auth0Provider } from '@auth0/auth0-react'

const mockedAuth0Provider = vi.mocked(Auth0Provider)

describe('Auth0ProviderWithNavigate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('VITE_AUTH0_DOMAIN', 'test.auth0.com')
    vi.stubEnv('VITE_AUTH0_CLIENT_ID', 'test-client-id')
    vi.stubEnv('VITE_AUTH0_AUDIENCE', 'https://test-api.example.com')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should render Auth0Provider with correct configuration', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    expect(screen.getByTestId('auth0-provider')).toBeInTheDocument()
    expect(screen.getByText('Test Child')).toBeInTheDocument()
  })

  it('should pass domain and clientId to Auth0Provider', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderWithConfigOptions
    expect(callArgs.domain).toBe('test.auth0.com')
    expect(callArgs.clientId).toBe('test-client-id')
  })

  it('should configure authorizationParams with audience and redirect_uri', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderWithConfigOptions
    expect(callArgs.authorizationParams).toBeDefined()
    expect(callArgs.authorizationParams?.audience).toBe('https://test-api.example.com')
    expect(callArgs.authorizationParams?.redirect_uri).toBeDefined()
  })

  it('should configure cacheLocation as memory for security', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderWithConfigOptions
    expect(callArgs.cacheLocation).toBe('memory')
  })

  it('should configure useRefreshTokens for secure token management', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderWithConfigOptions
    expect(callArgs.useRefreshTokens).toBe(true)
  })
})
