import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { FilterPanel } from './FilterPanel'
import { mockCategories } from '../../test/mocks/products'

const baseProps = {
  categories: mockCategories,
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  inStockOnly: false,
  onCategoryChange: vi.fn(),
  onPriceChange: vi.fn(),
  onInStockChange: vi.fn(),
  onClear: vi.fn(),
}

describe('FilterPanel', () => {
  it('should render the filters section with all controls', () => {
    renderWithProviders(<FilterPanel {...baseProps} />)

    expect(screen.getByRole('region', { name: /product filters/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /category/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/minimum price/i)).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: /in stock only/i })).toBeInTheDocument()
  })

  it('should not show a "Clear all" button when no filters are active', () => {
    renderWithProviders(<FilterPanel {...baseProps} />)
    expect(screen.queryByRole('button', { name: /clear all/i })).not.toBeInTheDocument()
  })

  it('should show a "Clear all" button when a filter is active', () => {
    renderWithProviders(<FilterPanel {...baseProps} categoryId="electronics" />)
    expect(screen.getByRole('button', { name: /clear all/i })).toBeInTheDocument()
  })

  it('should call onClear when "Clear all" is clicked', async () => {
    const onClear = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<FilterPanel {...baseProps} inStockOnly onClear={onClear} />)

    await user.click(screen.getByRole('button', { name: /clear all/i }))

    expect(onClear).toHaveBeenCalledTimes(1)
  })
})
