import { describe, it, expect, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { screen, waitFor, within, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ProductReviewsSection } from './ProductReviewsSection'

const API_BASE = 'http://localhost:8080/api'
const PRODUCT_ID = 'prod-42'

const SUMMARY = {
  averageRating: 4.5,
  count: 2,
  distribution: { '5': 1, '4': 1 },
}

function review(id: string, title: string) {
  return {
    id,
    productId: PRODUCT_ID,
    userId: 'auth0|someone',
    rating: 5,
    title,
    body: 'Body text',
    verified: true,
    helpful: 1,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function pageResponse(page: number, totalPages: number, content: unknown[]) {
  return { content, page, size: 5, totalElements: totalPages * 5, totalPages, sort: 'helpful' }
}

const server = setupServer(
  http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}/summary`, () => HttpResponse.json(SUMMARY)),
  http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}`, ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
    if (page === 0) return HttpResponse.json(pageResponse(0, 2, [review('r1', 'First review')]))
    return HttpResponse.json(pageResponse(1, 2, [review('r2', 'Second review')]))
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  delete window.__getAuthUserId
  delete window.__getAuthToken
})
afterAll(() => server.close())

describe('ProductReviewsSection', () => {
  beforeEach(() => {
    server.resetHandlers(
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}/summary`, () => HttpResponse.json(SUMMARY)),
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}`, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page') ?? '0')
        if (page === 0) return HttpResponse.json(pageResponse(0, 2, [review('r1', 'First review')]))
        return HttpResponse.json(pageResponse(1, 2, [review('r2', 'Second review')]))
      }),
    )
  })

  it('should render the rating summary once loaded', async () => {
    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    expect(await screen.findByText('4.5')).toBeInTheDocument()
    expect(screen.getByText('Based on 2 reviews')).toBeInTheDocument()
  })

  it('should render the first page of reviews and paginate to the next page', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    expect(await screen.findByText('First review')).toBeInTheDocument()
    expect(screen.queryByText('Second review')).not.toBeInTheDocument()

    const list = screen.getByTestId('review-list')
    await user.click(within(list).getByRole('button', { name: 'Go to page 2' }))

    expect(await screen.findByText('Second review')).toBeInTheDocument()
    expect(screen.queryByText('First review')).not.toBeInTheDocument()
  })

  it('should show the sign-in prompt (not the review form) when unauthenticated', async () => {
    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    await screen.findByText('4.5')
    expect(screen.getByTestId('review-signin-prompt')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /submit review/i })).not.toBeInTheDocument()
  })

  it('should show the review form (not the sign-in prompt) when authenticated', async () => {
    window.__getAuthUserId = () => 'auth0|abc123'

    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    await screen.findByText('4.5')
    expect(screen.getByRole('button', { name: /submit review/i })).toBeInTheDocument()
    expect(screen.queryByTestId('review-signin-prompt')).not.toBeInTheDocument()
  })

  it('should submit a review with the Authorization header and refresh the summary/list', async () => {
    window.__getAuthUserId = () => 'auth0|abc123'
    window.__getAuthToken = async () => 'test-jwt-token'

    let capturedAuthHeader: string | null = null
    let capturedBody: unknown
    let summaryHits = 0

    server.use(
      http.post(`${API_BASE}/v1/reviews`, async ({ request }) => {
        capturedAuthHeader = request.headers.get('Authorization')
        capturedBody = await request.json()
        return HttpResponse.json(review('r3', 'New review'), { status: 201 })
      }),
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}/summary`, () => {
        summaryHits += 1
        return HttpResponse.json(SUMMARY)
      }),
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}`, () =>
        HttpResponse.json(pageResponse(0, 1, [review('r1', 'First review')])),
      ),
    )

    const user = userEvent.setup()
    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    await screen.findByText('4.5')
    // fireEvent (not userEvent) for the star click — see StarRating.test.tsx for why.
    fireEvent.click(screen.getByRole('radio', { name: '5 Stars' }))
    await user.type(screen.getByLabelText('Title', { exact: false }), 'New review')
    await user.type(screen.getByLabelText('Your review', { exact: false }), 'Body text')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    await waitFor(() => expect(capturedBody).toEqual({
      productId: PRODUCT_ID,
      rating: 5,
      title: 'New review',
      body: 'Body text',
    }))
    expect(capturedAuthHeader).toBe('Bearer test-jwt-token')

    await waitFor(() => expect(summaryHits).toBeGreaterThan(1))
  })

  it('should show an error state with retry when the summary request fails', async () => {
    server.use(
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}/summary`, () =>
        HttpResponse.json({ message: 'boom' }, { status: 500 }),
      ),
    )

    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    // axiosRetry retries 500s on this idempotent GET 3x with exponential backoff before
    // surfacing the error, so give findBy more headroom than the 1s default.
    expect(await screen.findByText(/unable to load the rating summary/i, {}, { timeout: 5000 })).toBeInTheDocument()
  })

  it('should show an empty state when the product has no reviews', async () => {
    server.use(
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}/summary`, () =>
        HttpResponse.json({ averageRating: 0, count: 0, distribution: {} }),
      ),
      http.get(`${API_BASE}/v1/reviews/product/${PRODUCT_ID}`, () =>
        HttpResponse.json(pageResponse(0, 0, [])),
      ),
    )

    renderWithProviders(<ProductReviewsSection productId={PRODUCT_ID} />)

    expect(await screen.findByText(/be the first to review/i)).toBeInTheDocument()
    expect(await screen.findByTestId('review-list-empty')).toBeInTheDocument()
  })
})
