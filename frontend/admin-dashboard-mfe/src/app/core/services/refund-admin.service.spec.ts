import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RefundAdminService } from './refund-admin.service';
import { RefundSagaState } from '../models/refund.model';

const mockSaga: RefundSagaState = {
  id: 'saga-1',
  orderId: 'ord-1',
  userId: 'user-1',
  reason: 'Customer request',
  refundAmount: 115,
  status: 'PENDING',
  currentStep: 'VALIDATE',
  completedSteps: [],
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('RefundAdminService', () => {
  let service: RefundAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RefundAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RefundAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST to /api/orders/{orderId}/refund when starting a refund', () => {
    let result: RefundSagaState | undefined;
    service.startRefund('ord-1', { reason: 'Customer request' }).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/orders/ord-1/refund');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Customer request' });
    req.flush(mockSaga);
    expect(result).toEqual(mockSaga);
  });

  it('should GET /api/orders/refunds/{sagaId} when looking up a refund saga', () => {
    let result: RefundSagaState | undefined;
    service.getRefundSaga('saga-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/orders/refunds/saga-1');
    expect(req.request.method).toBe('GET');
    req.flush({ ...mockSaga, status: 'COMPLETED' });
    expect(result?.status).toBe('COMPLETED');
  });

  it('should propagate an error when the refund saga lookup 404s', () => {
    let error: unknown;
    service.getRefundSaga('missing').subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/orders/refunds/missing');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
    expect(error).toBeTruthy();
  });
});
