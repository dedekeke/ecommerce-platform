import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { CategorySelect } from './CategorySelect'
import { mockCategories } from '../../test/mocks/products'

describe('CategorySelect', () => {
  it('should render an "All Categories" option selected by default', () => {
    renderWithProviders(
      <CategorySelect categories={mockCategories} value={null} onChange={vi.fn()} />,
    )
    expect(screen.getByRole('combobox', { name: /category/i })).toHaveTextContent('All Categories')
  })

  it('should list root categories and indented children', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <CategorySelect categories={mockCategories} value={null} onChange={vi.fn()} />,
    )

    await user.click(screen.getByRole('combobox', { name: /category/i }))

    expect(screen.getByRole('option', { name: 'Electronics' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Smartphones/ })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Clothing' })).toBeInTheDocument()
  })

  it('should call onChange with the category slug when an option is selected', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(
      <CategorySelect categories={mockCategories} value={null} onChange={onChange} />,
    )

    await user.click(screen.getByRole('combobox', { name: /category/i }))
    await user.click(screen.getByRole('option', { name: 'Clothing' }))

    expect(onChange).toHaveBeenCalledWith('clothing')
  })

  it('should call onChange with null when "All Categories" is selected', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(
      <CategorySelect categories={mockCategories} value="clothing" onChange={onChange} />,
    )

    await user.click(screen.getByRole('combobox', { name: /category/i }))
    await user.click(screen.getByRole('option', { name: 'All Categories' }))

    expect(onChange).toHaveBeenCalledWith(null)
  })

  it('should disable the select while categories are loading', () => {
    renderWithProviders(
      <CategorySelect categories={[]} value={null} onChange={vi.fn()} isLoading />,
    )
    expect(screen.getByRole('combobox', { name: /category/i })).toHaveAttribute('aria-disabled', 'true')
  })
})
