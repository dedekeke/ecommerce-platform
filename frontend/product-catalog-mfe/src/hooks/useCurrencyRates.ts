import { useEffect, useState } from 'react'
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

/**
 * Fetches the static FX rate map from {@code /api/currency/rates} on first
 * mount and exposes a {@link RateMap}. The map is cached at module level so
 * multiple consumers do not duplicate the HTTP request (§3.5).
 *
 * Exposed for tests via {@link __resetCurrencyRatesCache}.
 */
export function useCurrencyRates(): UseCurrencyRatesResult {
  const [rates, setRates] = useState<RateMap>(cachedRates ?? {})
  const [isLoading, setIsLoading] = useState(!cachedRates)
  const [isError, setIsError] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let cancelled = false
    if (cachedRates) {
      setRates(cachedRates)
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    fetchRates()
      .then((r) => {
        if (!cancelled) {
          setRates(r)
          setIsLoading(false)
        }
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setIsError(true)
          setError(err)
          setIsLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { rates, isLoading, isError, error }
}

/**
 * Test-only escape hatch — wipes the module-level rate cache so hook tests
 * are independent.
 */
export function __resetCurrencyRatesCache(): void {
  cachedRates = null
  inflight = null
}
