import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useCategories } from './useCategories'
import { categoryService } from '../api'

vi.mock('../api', () => ({
  productService: {},
  categoryService: {
    getRootCategories: vi.fn(),
    getCategories: vi.fn(),
  },
}))

const mockGetRoot = vi.mocked(categoryService.getRootCategories)
const mockGetAll = vi.mocked(categoryService.getCategories)

const ROOT_CATS = [{ id: 'r1', name: 'Root' }]
const ALL_CATS = [{ id: 'a1', name: 'All' }]

describe('useCategories', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should fetch root categories when rootOnly is true (default)', async () => {
    mockGetRoot.mockResolvedValueOnce(ROOT_CATS as never)

    const { result } = renderHook(() => useCategories())

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.categories).toEqual(ROOT_CATS)
    expect(mockGetRoot).toHaveBeenCalledTimes(1)
    expect(mockGetAll).not.toHaveBeenCalled()
  })

  it('should fetch all categories when rootOnly is false', async () => {
    mockGetAll.mockResolvedValueOnce(ALL_CATS as never)

    const { result } = renderHook(() => useCategories(false))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.categories).toEqual(ALL_CATS)
    expect(mockGetAll).toHaveBeenCalledTimes(1)
  })

  it('should set isError when fetch fails', async () => {
    mockGetRoot.mockRejectedValueOnce(new Error('service error'))

    const { result } = renderHook(() => useCategories())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.categories).toEqual([])
  })

  it('should reset isLoading to true when rootOnly changes (FETCH_START regression)', async () => {
    mockGetRoot.mockResolvedValueOnce(ROOT_CATS as never)
    mockGetAll.mockResolvedValueOnce(ALL_CATS as never)

    const { result, rerender } = renderHook(
      (rootOnly: boolean) => useCategories(rootOnly),
      { initialProps: true }
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.categories).toEqual(ROOT_CATS)

    // Toggle rootOnly — isLoading must reset before the next fetch resolves.
    rerender(false)
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.categories).toEqual(ALL_CATS)
  })

  it('should reset isLoading to true on manual refetch', async () => {
    mockGetRoot
      .mockResolvedValueOnce(ROOT_CATS as never)
      .mockResolvedValueOnce(ROOT_CATS as never)

    const { result } = renderHook(() => useCategories())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
  })
})
