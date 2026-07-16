export type PromotionType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'BUY_X_GET_Y';

/** Mirrors promotion-service's PromotionResponse (GET/POST/PUT /api/promotions). */
export interface Promotion {
  id: number;
  code: string;
  name: string;
  description?: string;
  type: PromotionType;
  discountValue: number;
  minPurchaseAmount?: number;
  maxUses?: number;
  currentUses?: number;
  startDate: string;
  endDate: string;
  active?: boolean;
  applicableCategories?: number[];
  createdAt?: string;
  updatedAt?: string;
}

/** Body for POST/PUT /api/promotions (PromotionController's PromotionRequest). Admin-scoped. */
export interface PromotionPayload {
  code: string;
  name: string;
  description?: string;
  type: PromotionType;
  discountValue: number;
  minPurchaseAmount?: number;
  maxUses?: number;
  startDate: string;
  endDate: string;
  active?: boolean;
  applicableCategories?: number[];
}
