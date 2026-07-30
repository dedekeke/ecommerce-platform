import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { PriceRangeFilter } from './PriceRangeFilter'

describe('PriceRangeFilter', () => {
  it('should render empty min/max inputs by default', () => {
    renderWithProviders(<PriceRangeFilter minPrice={null} maxPrice={null} onChange={vi.fn()} />)
    expect(screen.getByLabelText(/minimum price/i)).toHaveValue(null)
    expect(screen.getByLabelText(/maximum price/i)).toHaveValue(null)
  })

  it('should render the provided min/max values', () => {
    renderWithProviders(<PriceRangeFilter minPrice={10} maxPrice={100} onChange={vi.fn()} />)
    expect(screen.getByLabelText(/minimum price/i)).toHaveValue(10)
    expect(screen.getByLabelText(/maximum price/i)).toHaveValue(100)
  })

  it('should debounce onChange after typing a min value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<PriceRangeFilter minPrice={null} maxPrice={null} onChange={onChange} debounceMs={50} />)

    await user.type(screen.getByLabelText(/minimum price/i), '25')

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(25, null))
  })

  it('should debounce onChange after typing a max value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<PriceRangeFilter minPrice={null} maxPrice={null} onChange={onChange} debounceMs={50} />)

    await user.type(screen.getByLabelText(/maximum price/i), '500')

    await waitFor(() => expect(onChange).toHaveBeenCalledWith(null, 500))
  })

  it('should show a validation error and not call onChange when min > max', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<PriceRangeFilter minPrice={null} maxPrice={null} onChange={onChange} debounceMs={50} />)

    await user.type(screen.getByLabelText(/minimum price/i), '100')
    await user.type(screen.getByLabelText(/maximum price/i), '10')

    await waitFor(() => expect(screen.getByLabelText(/maximum price/i)).toBeInvalid())
    expect(onChange).not.toHaveBeenCalledWith(100, 10)
  })

  it('should sync local input state when the min/max props change externally', () => {
    const { rerender } = renderWithProviders(
      <PriceRangeFilter minPrice={null} maxPrice={null} onChange={vi.fn()} />,
    )
    rerender(<PriceRangeFilter minPrice={5} maxPrice={50} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/minimum price/i)).toHaveValue(5)
    expect(screen.getByLabelText(/maximum price/i)).toHaveValue(50)
  })
})
