import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ReviewList } from './ReviewList'
import type { Review } from '../../types'

function makeReview(overrides: Partial<Review> = {}): Review {
  return {
    id: 'r1',
    productId: 'p1',
    userId: 'u1',
    rating: 5,
    title: 'Great',
    body: 'Loved it',
    verified: false,
    helpful: 0,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('ReviewList', () => {
  it('should render loading skeletons while isLoading', () => {
    renderWithProviders(
      <ReviewList reviews={[]} page={0} totalPages={0} isLoading onPageChange={vi.fn()} />,
    )
    expect(screen.getByTestId('review-list-skeleton')).toBeInTheDocument()
  })

  it('should render an error state with retry', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    renderWithProviders(
      <ReviewList reviews={[]} page={0} totalPages={0} isError onPageChange={vi.fn()} onRetry={onRetry} />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/unable to load reviews/i)
    await user.click(screen.getByRole('button', { name: /retry/i }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('should render the empty state when there are no reviews', () => {
    renderWithProviders(
      <ReviewList reviews={[]} page={0} totalPages={0} onPageChange={vi.fn()} />,
    )
    expect(screen.getByTestId('review-list-empty')).toBeInTheDocument()
  })

  it('should render one ReviewListItem per review', () => {
    const reviews = [makeReview({ id: 'r1', title: 'First' }), makeReview({ id: 'r2', title: 'Second' })]
    renderWithProviders(<ReviewList reviews={reviews} page={0} totalPages={1} onPageChange={vi.fn()} />)

    expect(screen.getAllByTestId('review-list-item')).toHaveLength(2)
    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('Second')).toBeInTheDocument()
  })

  it('should not render pagination when there is only one page', () => {
    renderWithProviders(<ReviewList reviews={[makeReview()]} page={0} totalPages={1} onPageChange={vi.fn()} />)
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('should call onPageChange with the zero-based page index when a page is clicked', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    renderWithProviders(
      <ReviewList reviews={[makeReview()]} page={0} totalPages={3} onPageChange={onPageChange} />,
    )

    await user.click(screen.getByRole('button', { name: 'Go to page 2' }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })
})
