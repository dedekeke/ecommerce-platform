import { describe, it, expect, vi } from 'vitest'
import { screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { InStockToggle } from './InStockToggle'

describe('InStockToggle', () => {
  it('should render unchecked with an accessible name', () => {
    renderWithProviders(<InStockToggle checked={false} onChange={vi.fn()} />)
    expect(screen.getByRole('switch', { name: /in stock only/i })).not.toBeChecked()
  })

  it('should render checked when checked is true', () => {
    renderWithProviders(<InStockToggle checked onChange={vi.fn()} />)
    expect(screen.getByRole('switch', { name: /in stock only/i })).toBeChecked()
  })

  it('should call onChange with the new value when toggled', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<InStockToggle checked={false} onChange={onChange} />)

    await user.click(screen.getByRole('switch', { name: /in stock only/i }))

    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('should be keyboard toggleable', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<InStockToggle checked={false} onChange={onChange} />)

    const toggle = screen.getByRole('switch', { name: /in stock only/i })
    act(() => toggle.focus())
    await user.keyboard('[Space]')

    expect(onChange).toHaveBeenCalledWith(true)
  })
})
