/** Mirrors promotion-service's LoyaltyResponse (GET /api/promotions/loyalty/{userId}). */
export interface LoyaltyStatus {
  tier: string;
  discountPercent: number;
  currentSpend: number;
  nextTierAt?: number;
  nextTier?: string;
}
