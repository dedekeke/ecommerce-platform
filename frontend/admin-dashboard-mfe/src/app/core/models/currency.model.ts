/** Mirrors promotion-service's CurrencyDtos.RatesResponse (GET /api/currency/rates). */
export interface CurrencyRates {
  base: string;
  rates: Record<string, number>;
}

/** Body for POST /api/currency/convert (CurrencyDtos.ConvertRequest). */
export interface ConvertCurrencyPayload {
  amount: number;
  from: string;
  to: string;
}

/** Mirrors promotion-service's CurrencyDtos.ConvertResponse. */
export interface ConvertCurrencyResult {
  amount: number;
  rate: number;
}
