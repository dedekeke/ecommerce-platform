import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrderService } from './order.service';
import { PagedOrders, Order } from '../models/order.model';

const mockOrder: Order = {
  id: 'order-1',
  orderNumber: 'ORD-001',
  userId: 'user-1',
  status: 'DELIVERED',
  lineItems: [],
  shipping: {
    carrier: 'FedEx',
    address: { street: '1 Main', city: 'NYC', state: 'NY', postalCode: '10001', country: 'US' },
  },
  payment: { method: 'CARD', subtotal: 50, shippingCost: 5, tax: 4, discount: 0, total: 59 },
  timeline: [],
  createdAt: '2024-01-10T00:00:00Z',
  updatedAt: '2024-01-15T00:00:00Z',
};

const mockPaged: PagedOrders = {
  content: [mockOrder],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OrderService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET paged orders for a user without status filter', () => {
    service
      .getOrdersForUser('user-1', { page: 0, size: 10 })
      .subscribe((paged) => expect(paged.content.length).toBe(1));

    const req = httpMock.expectOne((r) => r.url === '/api/orders/user/user-1');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush(mockPaged);
  });

  it('should include status in query params when provided', () => {
    service
      .getOrdersForUser('user-1', { page: 0, size: 10, status: 'DELIVERED' })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/orders/user/user-1');
    expect(req.request.params.get('status')).toBe('DELIVERED');
    req.flush(mockPaged);
  });

  it('should GET a single order by ID', () => {
    service.getOrderById('order-1').subscribe((o) => expect(o.orderNumber).toBe('ORD-001'));
    const req = httpMock.expectOne('/api/orders/order-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockOrder);
  });
});
