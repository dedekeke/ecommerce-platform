export type ReturnStatus =
  | 'REQUESTED'
  | 'NOTIFIED'
  | 'AWAITING_SHIPMENT'
  | 'RECEIVED'
  | 'INSPECTING'
  | 'APPROVED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

/** Mirrors order-service's ReturnLine (saga/rma/ReturnLine). */
export interface ReturnLine {
  id: string;
  orderItemId: string;
  productId?: string;
  quantity: number;
  unitPrice?: number;
  reason?: string;
  approved: boolean;
}

/** Mirrors order-service's Return entity (saga/rma/Return). */
export interface ReturnRequest {
  id: string;
  rmaNumber: string;
  orderId: string;
  userId: string;
  status: ReturnStatus;
  returnLabelUrl?: string;
  reason?: string;
  requestedAt: string;
  receivedAt?: string;
  inspectedAt?: string;
  outcome?: string;
  restockingFeePercent?: number;
  lines: ReturnLine[];
  condition?: string;
  notes?: string;
  refundSagaId?: string;
  failureReason?: string;
  updatedAt: string;
}

/** Body for POST /api/returns/{rmaId}/inspect (RmaController.InspectRequest). */
export interface InspectReturnPayload {
  outcome: 'APPROVED' | 'REJECTED';
  condition?: string;
  notes?: string;
  restockingFeePercent?: number;
}

/** List-row projection returned by GET /api/returns (no line items). */
export interface ReturnSummary {
  id: string;
  rmaNumber: string;
  orderId: string;
  userId: string;
  status: ReturnStatus;
  outcome?: string;
  refundSagaId?: string;
  requestedAt: string;
  updatedAt: string;
}

export interface PagedReturns {
  content: ReturnSummary[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
  numberOfElements?: number;
  empty?: boolean;
}

export interface ReturnFilterParams {
  status?: ReturnStatus;
  page: number;
  size: number;
}
