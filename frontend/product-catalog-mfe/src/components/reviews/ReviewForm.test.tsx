import { describe, it, expect, afterEach, beforeAll, afterAll, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ReviewForm } from './ReviewForm'

const API_BASE = 'http://localhost:8080/api'
const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('ReviewForm', () => {
  it('should render rating, title, and body fields with accessible labels', () => {
    renderWithProviders(<ReviewForm productId="p1" />)

    expect(screen.getByText('Your rating')).toBeInTheDocument()
    expect(screen.getByLabelText('Title', { exact: false })).toBeInTheDocument()
    expect(screen.getByLabelText('Your review', { exact: false })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /submit review/i })).toBeInTheDocument()
  })

  it('should show validation errors and not submit when fields are empty', async () => {
    const user = userEvent.setup()
    let called = false
    server.use(
      http.post(`${API_BASE}/v1/reviews`, () => {
        called = true
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    renderWithProviders(<ReviewForm productId="p1" />)
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/please select a rating/i)).toBeInTheDocument()
    expect(screen.getByText(/title is required/i)).toBeInTheDocument()
    expect(screen.getByText(/review text is required/i)).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('should submit to POST /v1/reviews with the entered rating, title, and body', async () => {
    const user = userEvent.setup()
    let capturedBody: unknown
    server.use(
      http.post(`${API_BASE}/v1/reviews`, async ({ request }) => {
        capturedBody = await request.json()
        return HttpResponse.json(
          {
            id: 'rev-1',
            productId: 'p1',
            userId: 'auth0|abc',
            rating: 5,
            title: 'Great product',
            body: 'Really happy with it.',
            verified: false,
            helpful: 0,
            createdAt: '2026-01-01T00:00:00Z',
          },
          { status: 201 },
        )
      }),
    )

    const onSubmitted = vi.fn()
    renderWithProviders(<ReviewForm productId="p1" onSubmitted={onSubmitted} />)

    fireEvent.click(screen.getByRole('radio', { name: '5 Stars' }))
    await user.type(screen.getByLabelText('Title', { exact: false }), 'Great product')
    await user.type(screen.getByLabelText('Your review', { exact: false }), 'Really happy with it.')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    await waitFor(() =>
      expect(capturedBody).toEqual({
        productId: 'p1',
        rating: 5,
        title: 'Great product',
        body: 'Really happy with it.',
      }),
    )

    expect(await screen.findByText(/thanks for your review/i)).toBeInTheDocument()
    expect(onSubmitted).toHaveBeenCalledOnce()
    expect(screen.getByLabelText('Title', { exact: false })).toHaveValue('')
  })

  it('should show the server error message and keep the entered values on failure', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${API_BASE}/v1/reviews`, () => {
        return HttpResponse.json({ message: 'You have already reviewed this product.' }, { status: 409 })
      }),
    )

    renderWithProviders(<ReviewForm productId="p1" />)

    fireEvent.click(screen.getByRole('radio', { name: '3 Stars' }))
    await user.type(screen.getByLabelText('Title', { exact: false }), 'Meh')
    await user.type(screen.getByLabelText('Your review', { exact: false }), 'It was okay.')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already reviewed/i)
    expect(screen.getByLabelText('Title', { exact: false })).toHaveValue('Meh')
  })

  it('should disable the submit button while the request is in flight', async () => {
    const user = userEvent.setup()
    let resolveFn!: () => void
    server.use(
      http.post(`${API_BASE}/v1/reviews`, async () => {
        await new Promise<void>((resolve) => { resolveFn = resolve })
        return HttpResponse.json(
          { id: 'r1', productId: 'p1', userId: 'u1', rating: 5, title: 't', body: 'b', verified: false, helpful: 0, createdAt: '2026-01-01T00:00:00Z' },
          { status: 201 },
        )
      }),
    )

    renderWithProviders(<ReviewForm productId="p1" />)
    fireEvent.click(screen.getByRole('radio', { name: '5 Stars' }))
    await user.type(screen.getByLabelText('Title', { exact: false }), 'Title here')
    await user.type(screen.getByLabelText('Your review', { exact: false }), 'Body here')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    await waitFor(() => expect(screen.getByRole('button', { name: /submitting/i })).toBeDisabled())

    resolveFn()
    await waitFor(() => expect(screen.getByRole('button', { name: /submit review/i })).not.toBeDisabled())
  })
})
