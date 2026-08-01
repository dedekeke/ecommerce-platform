import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ReviewSummary } from './ReviewSummary'

const SUMMARY = {
  averageRating: 4.2,
  count: 10,
  distribution: { '5': 6, '4': 2, '3': 1, '2': 1, '1': 0 },
}

describe('ReviewSummary', () => {
  it('should render loading skeletons while isLoading', () => {
    renderWithProviders(<ReviewSummary summary={null} isLoading />)
    expect(screen.getByTestId('review-summary-skeleton')).toBeInTheDocument()
  })

  it('should render the average rating, count, and per-star distribution', () => {
    renderWithProviders(<ReviewSummary summary={SUMMARY} />)

    expect(screen.getByText('4.2')).toBeInTheDocument()
    expect(screen.getByText('Based on 10 reviews')).toBeInTheDocument()
    expect(screen.getByLabelText('5 star: 6 reviews')).toBeInTheDocument()
    expect(screen.getByLabelText('1 star: 0 reviews')).toBeInTheDocument()
  })

  it('should render the empty state when count is 0', () => {
    renderWithProviders(<ReviewSummary summary={{ averageRating: 0, count: 0, distribution: {} }} />)
    expect(screen.getByTestId('review-summary-empty')).toBeInTheDocument()
    expect(screen.getByText(/be the first to review/i)).toBeInTheDocument()
  })

  it('should render the empty state when summary is null', () => {
    renderWithProviders(<ReviewSummary summary={null} />)
    expect(screen.getByTestId('review-summary-empty')).toBeInTheDocument()
  })

  it('should render an error state with a retry action', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    renderWithProviders(<ReviewSummary summary={null} isError onRetry={onRetry} />)

    expect(screen.getByRole('alert')).toHaveTextContent(/unable to load/i)
    await user.click(screen.getByRole('button', { name: /retry/i }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('should use singular "review" wording when count is 1', () => {
    renderWithProviders(<ReviewSummary summary={{ averageRating: 5, count: 1, distribution: { '5': 1 } }} />)
    expect(screen.getByText('Based on 1 review')).toBeInTheDocument()
  })
})
