import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderHistoryPage } from './order-history.page';
import { OrderService } from '../../core/services/order.service';
import { of, throwError } from 'rxjs';
import { PagedOrders, Order } from '../../core/models/order.model';

const mockOrder: Order = {
  orderId: 'order-nav-1',
  orderNumber: 'ORD-NAV-001',
  status: 'SHIPPED',
  currency: 'USD',
  subtotal: 30,
  tax: 3,
  shippingCost: 0,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 33,
  items: [],
  shippingAddress: { street: '2 Elm St', city: 'Boston', state: 'MA', postalCode: '02101', country: 'US' },
  paymentIntentId: 'pi_1',
  guestOrder: false,
  carrier: 'UPS',
  trackingNumber: null,
  shippedAt: '2024-02-01T10:00:00Z',
  deliveredAt: null,
  createdAt: '2024-02-01T00:00:00Z',
  updatedAt: '2024-02-01T00:00:00Z',
};

const emptyPaged: PagedOrders = { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 };
const singlePaged: PagedOrders = { content: [mockOrder], totalElements: 1, totalPages: 1, size: 10, number: 0 };

describe('OrderHistoryPage — navigation and interactions', () => {
  let fixture: ComponentFixture<OrderHistoryPage>;
  let component: OrderHistoryPage;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let router: Router;

  beforeEach(async () => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrdersForUser']);
    orderServiceSpy.getOrdersForUser.and.returnValue(of(singlePaged));

    await TestBed.configureTestingModule({
      imports: [OrderHistoryPage, NoopAnimationsModule],
      providers: [
        provideRouter([{ path: 'orders/:id', redirectTo: '' }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderHistoryPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should navigate to order detail when onViewOrder is called', () => {
    const navigateSpy = spyOn(router, 'navigate');
    component.onViewOrder('order-nav-1');
    expect(navigateSpy).toHaveBeenCalledWith(['orders', 'order-nav-1']);
  });

  it('should reset page to 0 and re-fetch on status change', () => {
    component.currentPage.set(2);
    component.selectedStatus = 'SHIPPED';
    component.onStatusChange();
    expect(component.currentPage()).toBe(0);
    expect(orderServiceSpy.getOrdersForUser).toHaveBeenCalledTimes(2);
  });

  it('should update current page and re-fetch on page change', () => {
    component.onPageChange({ pageIndex: 2, pageSize: 10, length: 30 });
    expect(component.currentPage()).toBe(2);
    expect(orderServiceSpy.getOrdersForUser).toHaveBeenCalledTimes(2);
  });

  it('should show empty state when orders list is empty', async () => {
    orderServiceSpy.getOrdersForUser.and.returnValue(of(emptyPaged));
    const emptyFixture = TestBed.createComponent(OrderHistoryPage);
    emptyFixture.detectChanges();
    await emptyFixture.whenStable();
    emptyFixture.detectChanges();
    expect(emptyFixture.nativeElement.textContent).toContain('No orders found');
  });

  it('should stop loading and show empty state on service error', async () => {
    orderServiceSpy.getOrdersForUser.and.returnValue(throwError(() => new Error('Network error')));
    const errorFixture = TestBed.createComponent(OrderHistoryPage);
    errorFixture.detectChanges();
    await errorFixture.whenStable();
    errorFixture.detectChanges();
    expect(errorFixture.componentInstance.loading()).toBeFalse();
  });
});
