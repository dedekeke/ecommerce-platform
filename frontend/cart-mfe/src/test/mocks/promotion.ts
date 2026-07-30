import type { PromotionValidationResponse } from '../../api/types'

export const mockValidDiscount: PromotionValidationResponse = {
  valid: true,
  message: 'Promotion is valid',
  discountAmount: 10,
  finalAmount: 90,
  promotionCode: 'SAVE10',
  promotionName: '10 Off Sale',
}

export const mockInvalidDiscount: PromotionValidationResponse = {
  valid: false,
  message: 'Promotion code not found or not valid at this time',
  discountAmount: null,
  finalAmount: null,
  promotionCode: null,
  promotionName: null,
}
