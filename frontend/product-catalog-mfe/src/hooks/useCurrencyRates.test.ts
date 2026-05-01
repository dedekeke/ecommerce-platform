import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { __resetCurrencyRatesCache, useCurrencyRates } from './useCurrencyRates'
import { apiClient } from '../api/apiClient'

vi.mock('../api/apiClient', () => ({
  apiClient: {
    get: vi.fn(),
  },
}))

const mockedGet = vi.mocked(apiClient.get)

describe('useCurrencyRates', () => {
  beforeEach(() => {
    __resetCurrencyRatesCache()
    vi.clearAllMocks()
  })

  afterEach(() => {
    __resetCurrencyRatesCache()
  })

  it('should_fetchRatesOnMount_andExposeRateMap', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { base: 'USD', rates: { USD: 1, EUR: 0.92, JPY: 149.5 } },
    })

    const { result } = renderHook(() => useCurrencyRates())

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.rates).toEqual({ USD: 1, EUR: 0.92, JPY: 149.5 })
    expect(result.current.isError).toBe(false)
    expect(mockedGet).toHaveBeenCalledWith('/currency/rates')
  })

  it('should_cacheRates_acrossMultipleHookConsumers', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { base: 'USD', rates: { USD: 1, EUR: 0.92 } },
    })

    const { result: first } = renderHook(() => useCurrencyRates())
    await waitFor(() => expect(first.current.isLoading).toBe(false))

    const { result: second } = renderHook(() => useCurrencyRates())
    await waitFor(() => expect(second.current.isLoading).toBe(false))

    expect(mockedGet).toHaveBeenCalledTimes(1)
    expect(second.current.rates.EUR).toBe(0.92)
  })

  it('should_setError_when_fetchFails', async () => {
    const failure = new Error('network down')
    mockedGet.mockRejectedValueOnce(failure)

    const { result } = renderHook(() => useCurrencyRates())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.error).toBe(failure)
    expect(result.current.rates).toEqual({})
  })

  it('should_returnEmptyMap_when_responseHasNoRates', async () => {
    mockedGet.mockResolvedValueOnce({ data: {} })

    const { result } = renderHook(() => useCurrencyRates())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.rates).toEqual({})
  })
})
