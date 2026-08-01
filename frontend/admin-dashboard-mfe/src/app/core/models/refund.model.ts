export type RefundSagaStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'COMPENSATING'
  | 'FAILED'
  | 'COMPENSATED';

export type RefundSagaStep =
  | 'VALIDATE'
  | 'REVERSE_PAYMENT'
  | 'RESTORE_INVENTORY'
  | 'UPDATE_ORDER'
  | 'NOTIFY';

/** Mirrors order-service's RefundSagaState (saga/refund/RefundSagaState). */
export interface RefundSagaState {
  id: string;
  orderId: string;
  userId?: string;
  reason?: string;
  refundAmount?: number;
  refundAmountOverride?: number;
  restockingFeePercent?: number;
  paymentIntentId?: string;
  refundTransactionId?: string;
  restorationId?: string;
  status: RefundSagaStatus;
  currentStep: RefundSagaStep;
  completedSteps?: RefundSagaStep[];
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

/** Body for POST /api/orders/{orderId}/refund (RefundController.RefundRequest). */
export interface StartRefundPayload {
  reason?: string;
}

export interface PagedRefunds {
  content: RefundSagaState[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
  numberOfElements?: number;
  empty?: boolean;
}

export interface RefundFilterParams {
  status?: RefundSagaStatus;
  page: number;
  size: number;
}
