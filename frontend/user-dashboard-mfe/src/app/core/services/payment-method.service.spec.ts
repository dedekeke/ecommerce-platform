import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentMethodService } from './payment-method.service';
import { SavedPaymentMethod, SetupIntentResponse } from '../models/payment-method.model';

describe('PaymentMethodService', () => {
  let service: PaymentMethodService;
  let httpMock: HttpTestingController;

  const mockMethod: SavedPaymentMethod = {
    id: 1,
    userId: 'me',
    provider: 'stripe',
    providerId: 'pm_123',
    last4: '4242',
    brand: 'visa',
    expMonth: 12,
    expYear: 2030,
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PaymentMethodService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentMethodService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/payments/methods/user/{userId} when listing methods', () => {
    let received: SavedPaymentMethod[] | undefined;
    service.list('me').subscribe((methods) => (received = methods));

    const req = httpMock.expectOne('/api/payments/methods/user/me');
    expect(req.request.method).toBe('GET');
    req.flush([mockMethod]);

    expect(received).toEqual([mockMethod]);
  });

  it('should POST /api/payments/methods/setup-intent when creating a setup intent', () => {
    const response: SetupIntentResponse = { setupIntentId: 'seti_1', clientSecret: 'secret_1' };
    let received: SetupIntentResponse | undefined;
    service.createSetupIntent().subscribe((r) => (received = r));

    const req = httpMock.expectOne('/api/payments/methods/setup-intent');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(response);

    expect(received).toEqual(response);
  });

  it('should POST /api/payments/methods/confirm with the setupIntentId when confirming', () => {
    let received: SavedPaymentMethod | undefined;
    service.confirmSetupIntent('seti_1').subscribe((r) => (received = r));

    const req = httpMock.expectOne('/api/payments/methods/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ setupIntentId: 'seti_1' });
    req.flush(mockMethod);

    expect(received).toEqual(mockMethod);
  });

  it('should DELETE /api/payments/methods/{id} when deleting a method', () => {
    let completed = false;
    service.delete(1).subscribe(() => (completed = true));

    const req = httpMock.expectOne('/api/payments/methods/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBeTrue();
  });

  it('should PUT /api/payments/methods/{id}/default when setting a method as default', () => {
    let received: SavedPaymentMethod | undefined;
    service.setDefault(1).subscribe((r) => (received = r));

    const req = httpMock.expectOne('/api/payments/methods/1/default');
    expect(req.request.method).toBe('PUT');
    req.flush(mockMethod);

    expect(received).toEqual(mockMethod);
  });
});
