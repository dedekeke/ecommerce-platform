import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderDetailPage } from './order-detail.page';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderService } from '../../core/services/order.service';
import { of } from 'rxjs';
import { Order } from '../../core/models/order.model';

const mockOrder: Order = {
  id: 'order-1',
  orderNumber: 'ORD-001',
  userId: 'user-1',
  status: 'DELIVERED',
  lineItems: [
    {
      id: 'li-1',
      productId: 'p1',
      productName: 'Widget A',
      quantity: 2,
      unitPrice: 20,
      totalPrice: 40,
      sku: 'WA-01',
    },
  ],
  shipping: {
    carrier: 'FedEx',
    trackingNumber: 'TRACK123',
    address: { street: '1 Main', city: 'NYC', state: 'NY', postalCode: '10001', country: 'US' },
  },
  payment: { method: 'CREDIT_CARD', last4: '4242', subtotal: 40, shippingCost: 5, tax: 3.5, discount: 0, total: 48.5 },
  timeline: [
    { status: 'PENDING', timestamp: '2024-01-10T08:00:00Z' },
    { status: 'DELIVERED', timestamp: '2024-01-15T10:00:00Z' },
  ],
  createdAt: '2024-01-10T00:00:00Z',
  updatedAt: '2024-01-15T00:00:00Z',
};

describe('OrderDetailPage', () => {
  let fixture: ComponentFixture<OrderDetailPage>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrderById']);
    orderServiceSpy.getOrderById.and.returnValue(of(mockOrder));

    await TestBed.configureTestingModule({
      imports: [OrderDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'order-1' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetailPage);
    fixture.detectChanges();
  });

  it('should display order number', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ORD-001');
  });

  it('should display line items', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Widget A');
  });

  it('should display the order timeline', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const timeline = fixture.nativeElement.querySelector('app-order-timeline');
    expect(timeline).toBeTruthy();
  });

  it('should display shipping carrier', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('FedEx');
  });

  it('should display total amount', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('48.5');
  });
});
