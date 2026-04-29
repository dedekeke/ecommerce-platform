import { describe, it, expect } from 'vitest'
import { renderWithProviders } from '../test/renderWithProviders'
import CartItemSkeleton from './CartItemSkeleton'

describe('CartItemSkeleton', () => {
  it('should render skeleton placeholder elements', () => {
    renderWithProviders(<CartItemSkeleton />)
    const skeletons = document.querySelectorAll('.MuiSkeleton-root')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)
  })

  it('should have an accessible container', () => {
    const { container } = renderWithProviders(<CartItemSkeleton />)
    expect(container.firstChild).toBeInTheDocument()
  })
})
