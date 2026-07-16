import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RefundAdminService } from './refund-admin.service';
import { PagedRefunds, RefundSagaState } from '../models/refund.model';

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

  it('should fetch paginated refund sagas', () => {
    const mockPaged: PagedRefunds = {
      content: [mockSaga],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
    };
    let result: PagedRefunds | undefined;
    service.getRefunds({ page: 0, size: 20 }).subscribe((r) => (result = r));

    const req = httpMock.expectOne((r) => r.url === '/api/orders/refunds');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(mockPaged);
    expect(result).toEqual(mockPaged);
  });

  it('should include status filter when provided when fetching refunds', () => {
    service.getRefunds({ page: 0, size: 20, status: 'COMPLETED' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/orders/refunds');
    expect(req.request.params.get('status')).toBe('COMPLETED');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });

  it('should not include a status param when not provided when fetching refunds', () => {
    service.getRefunds({ page: 0, size: 20 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/orders/refunds');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });
});
