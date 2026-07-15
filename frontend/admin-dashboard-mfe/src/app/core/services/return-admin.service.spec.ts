import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReturnAdminService } from './return-admin.service';
import { ReturnRequest } from '../models/return.model';

const mockReturn: ReturnRequest = {
  id: 'rma-1',
  rmaNumber: 'RMA-ABC123',
  orderId: 'ord-1',
  userId: 'user-1',
  status: 'AWAITING_SHIPMENT',
  reason: 'Wrong size',
  requestedAt: '2024-01-01T00:00:00Z',
  lines: [
    { id: 'line-1', orderItemId: 'item-1', productId: 'prod-1', quantity: 2, unitPrice: 25, approved: false },
  ],
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('ReturnAdminService', () => {
  let service: ReturnAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ReturnAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReturnAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/returns/{rmaId} when fetching a return by id', () => {
    let result: ReturnRequest | undefined;
    service.getReturnById('rma-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/returns/rma-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockReturn);
    expect(result).toEqual(mockReturn);
  });

  it('should GET /api/returns/user/{userId} when listing a user\'s returns', () => {
    let result: ReturnRequest[] | undefined;
    service.getReturnsByUser('user-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/returns/user/user-1');
    expect(req.request.method).toBe('GET');
    req.flush([mockReturn]);
    expect(result).toEqual([mockReturn]);
  });

  it('should POST to /api/returns/{rmaId}/receive when marking a return received', () => {
    let result: ReturnRequest | undefined;
    service.markReceived('rma-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/returns/rma-1/receive');
    expect(req.request.method).toBe('POST');
    req.flush({ ...mockReturn, status: 'RECEIVED' });
    expect(result?.status).toBe('RECEIVED');
  });

  it('should POST to /api/returns/{rmaId}/inspect with the inspection payload', () => {
    let result: ReturnRequest | undefined;
    const payload = { outcome: 'APPROVED' as const, condition: 'NEW', restockingFeePercent: 10 };
    service.inspect('rma-1', payload).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/returns/rma-1/inspect');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...mockReturn, status: 'APPROVED', outcome: 'APPROVED' });
    expect(result?.outcome).toBe('APPROVED');
  });

  it('should propagate an error when the return lookup 404s', () => {
    let error: unknown;
    service.getReturnById('missing').subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/returns/missing');
    req.flush({ error: 'Return not found' }, { status: 404, statusText: 'Not Found' });
    expect(error).toBeTruthy();
  });
});
