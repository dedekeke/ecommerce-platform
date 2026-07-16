import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrderAdminService } from './order-admin.service';
import { Order, PagedOrders } from '../models/order.model';

const mockOrder: Order = {
  orderId: 'ord-1',
  orderNumber: 'ORD-0001',
  status: 'PENDING',
  currency: 'USD',
  subtotal: 100,
  tax: 5,
  shippingCost: 10,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 115,
  items: [],
  shippingAddress: { street: '1 Main St', city: 'NY', state: 'NY', postalCode: '10001', country: 'US' },
  paymentIntentId: 'pi_1',
  guestOrder: false,
  carrier: 'FedEx',
  trackingNumber: null,
  shippedAt: null,
  deliveredAt: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockPagedOrders: PagedOrders = {
  content: [mockOrder],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('OrderAdminService', () => {
  let service: OrderAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OrderAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch paginated orders', () => {
    let result: PagedOrders | undefined;
    service.getOrders({ page: 0, size: 10 }).subscribe((r) => (result = r));
    const req = httpMock.expectOne((r) => r.url === '/api/orders');
    expect(req.request.method).toBe('GET');
    req.flush(mockPagedOrders);
    expect(result).toEqual(mockPagedOrders);
  });

  it('should include status filter when provided', () => {
    service.getOrders({ page: 0, size: 10, status: 'PENDING' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/orders');
    expect(req.request.params.get('status')).toBe('PENDING');
    req.flush(mockPagedOrders);
  });

  it('should fetch order by id', () => {
    let result: Order | undefined;
    service.getOrderById('ord-1').subscribe((r) => (result = r));
    const req = httpMock.expectOne('/api/orders/ord-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockOrder);
    expect(result).toEqual(mockOrder);
  });

  it('should update order status via PUT with status as a query param, not a JSON body', () => {
    service.updateOrderStatus('ord-1', { status: 'CONFIRMED' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/orders/ord-1/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.params.get('status')).toBe('CONFIRMED');
    req.flush({ ...mockOrder, status: 'CONFIRMED' });
  });
});
