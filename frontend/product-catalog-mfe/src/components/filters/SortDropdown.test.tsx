import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { SortDropdown } from './SortDropdown'

describe('SortDropdown', () => {
  it('should show "Newest First" as the default label for createdAt/desc', () => {
    renderWithProviders(
      <SortDropdown sortBy="createdAt" sortDirection="desc" onSortChange={vi.fn()} />,
    )
    expect(screen.getByTestId('sort-dropdown')).toHaveTextContent('Newest First')
  })

  it('should call onSortChange with the selected sort field/direction', async () => {
    const onSortChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(
      <SortDropdown sortBy="createdAt" sortDirection="desc" onSortChange={onSortChange} />,
    )

    await user.click(screen.getByRole('combobox', { name: /sort by/i }))
    await user.click(screen.getByRole('option', { name: 'Price: Low to High' }))

    expect(onSortChange).toHaveBeenCalledWith('price', 'asc')
  })

  it('should be disabled when disabled is true', () => {
    renderWithProviders(
      <SortDropdown sortBy="createdAt" sortDirection="desc" onSortChange={vi.fn()} disabled />,
    )
    expect(screen.getByRole('combobox', { name: /sort by/i })).toHaveAttribute('aria-disabled', 'true')
  })

  it('should not be disabled by default', () => {
    renderWithProviders(
      <SortDropdown sortBy="createdAt" sortDirection="desc" onSortChange={vi.fn()} />,
    )
    expect(screen.getByRole('combobox', { name: /sort by/i })).not.toHaveAttribute('aria-disabled')
  })
})
