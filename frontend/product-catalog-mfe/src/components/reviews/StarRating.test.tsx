import { describe, it, expect, vi } from 'vitest'
import { screen, fireEvent } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { StarRating } from './StarRating'

describe('StarRating', () => {
  it('should render a read-only rating with an accessible label announcing the value', () => {
    renderWithProviders(<StarRating value={4} readOnly label="Product rating" />)
    expect(screen.getByLabelText('Product rating', { exact: false })).toBeInTheDocument()
  })

  it('should display the numeric value when showValue is set', () => {
    renderWithProviders(<StarRating value={4.5} readOnly showValue />)
    expect(screen.getByText('4.5')).toBeInTheDocument()
  })

  it('should expose radio inputs with "N Stars" labels when interactive', () => {
    renderWithProviders(<StarRating value={0} readOnly={false} label="Your rating" />)
    expect(screen.getByRole('radio', { name: '3 Stars' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '5 Stars' })).toBeInTheDocument()
  })

  it('should call onChange with the selected value when a star is clicked', () => {
    // fireEvent (not userEvent) — MUI Rating's hover-tracking mousemove handler divides by the
    // container's zero-sized jsdom/happy-dom bounding rect, which would otherwise turn the
    // click's implicit pointermove into a NaN value.
    const onChange = vi.fn()
    renderWithProviders(<StarRating value={0} readOnly={false} onChange={onChange} label="Your rating" />)

    fireEvent.click(screen.getByRole('radio', { name: '4 Stars' }))

    expect(onChange).toHaveBeenCalledWith(4)
  })

  it('should not render any radio inputs when readOnly', () => {
    renderWithProviders(<StarRating value={3} readOnly />)
    expect(screen.queryAllByRole('radio')).toHaveLength(0)
  })
})
