import { useEffect, useReducer } from 'react'
import { apiClient } from '../api/apiClient'

/**
 * Map of ISO 4217 currency code to its rate against USD (1 USD = `rate`
 * units of currency). USD itself is `1`. The hook caches the response in a
 * module-level singleton so multiple ProductCards on a page share one fetch.
 */
export type RateMap = Record<string, number>

interface RatesResponse {
  base: string
  rates: Record<string, number>
}

interface UseCurrencyRatesResult {
  rates: RateMap
  isLoading: boolean
  isError: boolean
  error: Error | null
}

let cachedRates: RateMap | null = null
let inflight: Promise<RateMap> | null = null

async function fetchRates(): Promise<RateMap> {
  if (cachedRates) return cachedRates
  if (inflight) return inflight
  inflight = apiClient
    .get<RatesResponse>('/currency/rates')
    .then((res) => {
      cachedRates = res.data?.rates ?? {}
      return cachedRates
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

interface RatesState {
  rates: RateMap
  isLoading: boolean
  isError: boolean
  error: Error | null
}

type RatesAction =
  | { type: 'SUCCESS'; payload: RateMap }
  | { type: 'ERROR'; payload: Error }

function reducer(state: RatesState, action: RatesAction): RatesState {
  switch (action.type) {
    case 'SUCCESS':
      return { rates: action.payload, isLoading: false, isError: false, error: null }
    case 'ERROR':
      return { ...state, isLoading: false, isError: true, error: action.payload }
  }
}

/**
 * Fetches the static FX rate map from {@code /api/currency/rates} on first
 * mount and exposes a {@link RateMap}. The map is cached at module level so
 * multiple consumers do not duplicate the HTTP request (§3.5).
 *
 * Exposed for tests via {@link __resetCurrencyRatesCache}.
 */
export function useCurrencyRates(): UseCurrencyRatesResult {
  // Initialise synchronously from cache when available so consumers never see
  // a loading flash on subsequent mounts within the same session.
  const [state, dispatch] = useReducer(reducer, undefined, () => ({
    rates: cachedRates ?? {},
    isLoading: !cachedRates,
    isError: false,
    error: null,
  }))

  useEffect(() => {
    if (cachedRates) return
    let cancelled = false
    fetchRates()
      .then((r) => {
        if (!cancelled) dispatch({ type: 'SUCCESS', payload: r })
      })
      .catch((err: Error) => {
        if (!cancelled) dispatch({ type: 'ERROR', payload: err })
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { rates: state.rates, isLoading: state.isLoading, isError: state.isError, error: state.error }
}

/**
 * Test-only escape hatch — wipes the module-level rate cache so hook tests
 * are independent.
 */
export function __resetCurrencyRatesCache(): void {
  cachedRates = null
  inflight = null
}
