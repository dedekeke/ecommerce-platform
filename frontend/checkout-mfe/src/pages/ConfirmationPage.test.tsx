import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { render } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { theme } from '../theme/theme'
import { handlers } from '../test/mocks/handlers'
import ConfirmationPage from './ConfirmationPage'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderWithRoute(orderId: string) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[`/confirmation/${orderId}`]}>
        <Routes>
          <Route path="/confirmation/:orderId" element={<ConfirmationPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  )
}

describe('ConfirmationPage', () => {
  it('should show a loading state while fetching', () => {
    renderWithRoute('order-123')
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })

  it('should show the order confirmation after loading', async () => {
    renderWithRoute('order-123')
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /order confirmed/i })).toBeInTheDocument()
    })
    expect(screen.getByText('ORD-20260429-001')).toBeInTheDocument()
  })

  it('should show an error state when the order fetch fails', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.get('http://localhost:8080/api/orders/:orderId', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 })
      )
    )
    renderWithRoute('bad-id')
    await waitFor(() => {
      expect(screen.getByText(/could not load order/i)).toBeInTheDocument()
    })
  })
})
