import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import ProductListPage from './ProductListPage'
import { useProducts, useSearchResults, useCategories } from '../hooks'
import { useProductFilterStore } from '../stores'
import { mockProducts, mockCategories, createMockProduct } from '../test/mocks/products'

vi.mock('../hooks', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../hooks')>()
  return { ...actual, useProducts: vi.fn(), useSearchResults: vi.fn(), useCategories: vi.fn() }
})

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, searchService: { ...actual.searchService, autocomplete: vi.fn().mockResolvedValue([]) } }
})

const mockUseProducts = vi.mocked(useProducts)
const mockUseSearchResults = vi.mocked(useSearchResults)
const mockUseCategories = vi.mocked(useCategories)

const BROWSE_RESULT = {
  products: mockProducts,
  totalElements: mockProducts.length,
  totalPages: 1,
  isLoading: false,
  isError: false,
  error: null,
  refetch: vi.fn(),
}

const EMPTY_SEARCH_RESULT = {
  products: [],
  totalElements: 0,
  totalPages: 0,
  isLoading: false,
  isError: false,
  error: null,
  refetch: vi.fn(),
}

describe('ProductListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useProductFilterStore.getState().resetFilters()
    mockUseProducts.mockReturnValue(BROWSE_RESULT)
    mockUseSearchResults.mockReturnValue(EMPTY_SEARCH_RESULT)
    mockUseCategories.mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    })
  })

  it('should render the product grid, search box and filter panel in browse mode', () => {
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })

    expect(screen.getAllByTestId('product-card')).toHaveLength(mockProducts.length)
    expect(screen.getByRole('combobox', { name: /search products/i })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: /product filters/i })).toBeInTheDocument()
    expect(mockUseProducts).toHaveBeenCalled()
    expect(mockUseSearchResults.mock.calls[0][1]).toMatchObject({ enabled: false })
  })

  it('should pass categoryId through to useProducts in browse mode', () => {
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products?category=electronics'] })

    expect(mockUseProducts.mock.calls.at(-1)?.[0]).toMatchObject({ categoryId: 'electronics' })
  })

  it('should show the error state when the browse query fails', () => {
    mockUseProducts.mockReturnValue({ ...BROWSE_RESULT, isLoading: false, isError: true, products: [] })
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })

    expect(screen.getByText(/failed to load products/i)).toBeInTheDocument()
  })

  it('should show the empty state with the search query in browse mode', () => {
    mockUseProducts.mockReturnValue({ ...BROWSE_RESULT, products: [], totalElements: 0 })
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products?q=zzz'] })

    expect(screen.getByText(/no products found for "zzz"/i)).toBeInTheDocument()
  })

  it('should switch to search-service results once a search query is present', async () => {
    mockUseSearchResults.mockReturnValue({
      ...EMPTY_SEARCH_RESULT,
      products: [createMockProduct({ id: 'search-1', name: 'Searched Sneakers' })],
      totalElements: 1,
      totalPages: 1,
    })

    renderWithProviders(<ProductListPage />, { initialEntries: ['/products?q=sneakers'] })

    await waitFor(() => expect(screen.getByText('Searched Sneakers')).toBeInTheDocument())
    expect(mockUseProducts.mock.calls.at(-1)?.[1]).toMatchObject({ enabled: false })
    expect(mockUseSearchResults.mock.calls.at(-1)?.[1]).toMatchObject({ enabled: true })
    expect(mockUseSearchResults.mock.calls.at(-1)?.[0]).toMatchObject({ q: 'sneakers' })
  })

  it('should disable sorting while a search query is active', () => {
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products?q=sneakers'] })
    expect(screen.getByRole('combobox', { name: /sort by/i })).toHaveAttribute('aria-disabled', 'true')
  })

  it('should not disable sorting in browse mode', () => {
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })
    expect(screen.getByRole('combobox', { name: /sort by/i })).not.toHaveAttribute('aria-disabled')
  })

  it('should run a new search and update the URL when the user submits ProductSearch', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })

    await user.type(screen.getByRole('combobox', { name: /search products/i }), 'hats')
    await user.keyboard('{Enter}')

    await waitFor(() => expect(useProductFilterStore.getState().searchQuery).toBe('hats'))
  })

  it('should update categoryId in the store when a category filter is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })

    await user.click(screen.getByRole('combobox', { name: /category/i }))
    await user.click(screen.getByRole('option', { name: 'Clothing' }))

    await waitFor(() => expect(useProductFilterStore.getState().categoryId).toBe('clothing'))
  })

  it('should reset category/price/stock filters (but keep the search query) when "Clear all" is used', async () => {
    useProductFilterStore.getState().setSearchQuery('sneakers')
    useProductFilterStore.getState().setCategory('electronics')
    useProductFilterStore.getState().setInStockOnly(true)
    const user = userEvent.setup()
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products?q=sneakers&category=electronics&inStock=true'] })

    await user.click(screen.getByRole('button', { name: /clear all/i }))

    await waitFor(() => {
      const state = useProductFilterStore.getState()
      expect(state.categoryId).toBeNull()
      expect(state.inStockOnly).toBe(false)
      expect(state.searchQuery).toBe('sneakers')
    })
  })

  it('should show pagination when there is more than one page', () => {
    mockUseProducts.mockReturnValue({ ...BROWSE_RESULT, totalPages: 3, totalElements: 30 })
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })

    expect(screen.getByTestId('pagination')).toBeInTheDocument()
  })

  it('should not show pagination for a single page', () => {
    renderWithProviders(<ProductListPage />, { initialEntries: ['/products'] })
    expect(screen.queryByTestId('pagination')).not.toBeInTheDocument()
  })
})
