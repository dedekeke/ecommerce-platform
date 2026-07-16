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
  orderId: 'order-1',
  orderNumber: 'ORD-001',
  status: 'DELIVERED',
  currency: 'USD',
  subtotal: 40,
  tax: 3.5,
  shippingCost: 5,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 48.5,
  items: [
    { productId: 'p1', productName: 'Widget A', price: 20, quantity: 2, subtotal: 40 },
  ],
  shippingAddress: { street: '1 Main', city: 'NYC', state: 'NY', postalCode: '10001', country: 'US' },
  paymentIntentId: 'pi_1',
  guestOrder: false,
  carrier: 'FedEx',
  trackingNumber: 'TRACK123',
  shippedAt: '2024-01-12T00:00:00Z',
  deliveredAt: '2024-01-15T10:00:00Z',
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

  it('should display the tracking number when present', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('TRACK123');
  });

  it('should derive a timeline with PENDING, SHIPPED and DELIVERED milestones', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const steps = fixture.nativeElement.querySelectorAll('.timeline__step');
    expect(steps.length).toBe(3);
  });
});
