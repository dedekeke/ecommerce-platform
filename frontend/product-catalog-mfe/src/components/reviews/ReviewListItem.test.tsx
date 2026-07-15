import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ReviewListItem } from './ReviewListItem'
import type { Review } from '../../types'

const REVIEW: Review = {
  id: 'r1',
  productId: 'p1',
  userId: 'auth0|sensitive-id',
  rating: 4,
  title: 'Solid purchase',
  body: 'Worked as expected.',
  verified: true,
  helpful: 7,
  createdAt: '2026-02-01T00:00:00Z',
}

describe('ReviewListItem', () => {
  it('should render the title, body, and helpful count', () => {
    renderWithProviders(<ReviewListItem review={REVIEW} />)

    expect(screen.getByText('Solid purchase')).toBeInTheDocument()
    expect(screen.getByText('Worked as expected.')).toBeInTheDocument()
    expect(screen.getByText('7 found this helpful')).toBeInTheDocument()
  })

  it('should show a "Verified Purchase" badge when verified', () => {
    renderWithProviders(<ReviewListItem review={REVIEW} />)
    expect(screen.getByText('Verified Purchase')).toBeInTheDocument()
  })

  it('should not show the badge when not verified', () => {
    renderWithProviders(<ReviewListItem review={{ ...REVIEW, verified: false }} />)
    expect(screen.queryByText('Verified Purchase')).not.toBeInTheDocument()
  })

  it('should never render the raw userId', () => {
    renderWithProviders(<ReviewListItem review={REVIEW} />)
    expect(screen.queryByText(/auth0\|sensitive-id/)).not.toBeInTheDocument()
  })

  it('should render an accessible star rating label', () => {
    renderWithProviders(<ReviewListItem review={REVIEW} />)
    expect(screen.getByLabelText('4 out of 5 stars', { exact: false })).toBeInTheDocument()
  })
})
