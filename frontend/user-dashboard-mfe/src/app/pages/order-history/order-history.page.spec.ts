import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderHistoryPage } from './order-history.page';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderService } from '../../core/services/order.service';
import { of } from 'rxjs';
import { PagedOrders, Order } from '../../core/models/order.model';

const mockOrder: Order = {
  orderId: 'order-1',
  orderNumber: 'ORD-001',
  status: 'DELIVERED',
  currency: 'USD',
  subtotal: 50,
  tax: 4,
  shippingCost: 5,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 59,
  items: [],
  shippingAddress: { street: '1 Main', city: 'NYC', state: 'NY', postalCode: '10001', country: 'US' },
  paymentIntentId: 'pi_1',
  guestOrder: false,
  carrier: 'FedEx',
  trackingNumber: null,
  shippedAt: null,
  deliveredAt: '2024-01-15T10:00:00Z',
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

describe('OrderHistoryPage', () => {
  let fixture: ComponentFixture<OrderHistoryPage>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrdersForUser']);
    orderServiceSpy.getOrdersForUser.and.returnValue(of(mockPaged));

    await TestBed.configureTestingModule({
      imports: [OrderHistoryPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderHistoryPage);
    fixture.detectChanges();
  });

  it('should display page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Order History');
  });

  it('should render order cards for loaded orders', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('app-order-card');
    expect(cards.length).toBe(1);
  });

  it('should call getOrdersForUser on init', () => {
    expect(orderServiceSpy.getOrdersForUser).toHaveBeenCalled();
  });

  it('should show pagination controls when multiple pages exist', async () => {
    orderServiceSpy.getOrdersForUser.and.returnValue(
      of({ ...mockPaged, totalPages: 3, totalElements: 30 })
    );
    const pagedFixture = TestBed.createComponent(OrderHistoryPage);
    pagedFixture.detectChanges();
    await pagedFixture.whenStable();
    pagedFixture.detectChanges();
    const paginator = pagedFixture.nativeElement.querySelector('mat-paginator');
    expect(paginator).toBeTruthy();
  });
});
