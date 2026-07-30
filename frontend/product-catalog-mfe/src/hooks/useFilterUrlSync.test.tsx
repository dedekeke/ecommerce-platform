import { describe, it, expect, beforeEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { useFilterUrlSync } from './useFilterUrlSync'
import { useProductFilterStore } from '../stores'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

function Harness() {
  useFilterUrlSync()
  return <LocationProbe />
}

function renderHarness(initialEntries: string[], path = '/products') {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path={path} element={<Harness />} />
        <Route path={`${path}/category/:categorySlug`} element={<Harness />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('useFilterUrlSync', () => {
  beforeEach(() => {
    act(() => {
      useProductFilterStore.getState().resetFilters()
    })
  })

  it('should hydrate the store from the initial q/category URL params', () => {
    renderHarness(['/products?q=shoes&category=electronics'])

    expect(useProductFilterStore.getState().searchQuery).toBe('shoes')
    expect(useProductFilterStore.getState().categoryId).toBe('electronics')
  })

  it('should hydrate minPrice/maxPrice/inStock from the URL', () => {
    renderHarness(['/products?minPrice=10&maxPrice=100&inStock=true'])

    const state = useProductFilterStore.getState()
    expect(state.minPrice).toBe(10)
    expect(state.maxPrice).toBe(100)
    expect(state.inStockOnly).toBe(true)
  })

  it('should default minPrice/maxPrice/inStock when absent from the URL', () => {
    renderHarness(['/products'])

    const state = useProductFilterStore.getState()
    expect(state.minPrice).toBeNull()
    expect(state.maxPrice).toBeNull()
    expect(state.inStockOnly).toBe(false)
  })

  it('should read the category from a :categorySlug route param when no query param is present', () => {
    renderHarness(['/products/category/fashion'])

    expect(useProductFilterStore.getState().categoryId).toBe('fashion')
  })

  it('should prefer the explicit category query param over the route param', () => {
    renderHarness(['/products/category/fashion?category=electronics'])

    expect(useProductFilterStore.getState().categoryId).toBe('electronics')
  })

  it('should write store filter changes back into the URL', () => {
    renderHarness(['/products'])

    act(() => {
      useProductFilterStore.getState().setCategory('electronics')
    })

    expect(screen.getByTestId('location')).toHaveTextContent('/products?category=electronics')
  })

  it('should write price range and in-stock changes back into the URL', () => {
    renderHarness(['/products'])

    act(() => {
      useProductFilterStore.getState().setPriceRange(10, 100)
      useProductFilterStore.getState().setInStockOnly(true)
    })

    const location = screen.getByTestId('location').textContent ?? ''
    expect(location).toContain('minPrice=10')
    expect(location).toContain('maxPrice=100')
    expect(location).toContain('inStock=true')
  })

  it('should remove a URL param once the matching filter is cleared', () => {
    renderHarness(['/products?category=electronics'])

    act(() => {
      useProductFilterStore.getState().setCategory(null)
    })

    expect(screen.getByTestId('location')).toHaveTextContent('/products')
    expect(screen.getByTestId('location')).not.toHaveTextContent('category=')
  })

  it('should not rewrite the URL when store values already match it (no loop)', () => {
    renderHarness(['/products?q=shoes'])

    const before = screen.getByTestId('location').textContent

    act(() => {
      useProductFilterStore.getState().setSearchQuery('shoes')
    })

    expect(screen.getByTestId('location').textContent).toBe(before)
  })
})
