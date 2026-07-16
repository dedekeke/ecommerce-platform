import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrdersAdminPage } from './orders-admin.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderAdminService } from '../../core/services/order-admin.service';
import { of } from 'rxjs';
import { Order, PagedOrders } from '../../core/models/order.model';

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
  items: [{ productId: 'p1', productName: 'Widget', price: 100, quantity: 1, subtotal: 100 }],
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

const mockPaged: PagedOrders = {
  content: [mockOrder],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('OrdersAdminPage', () => {
  let fixture: ComponentFixture<OrdersAdminPage>;
  let orderServiceSpy: jasmine.SpyObj<OrderAdminService>;

  beforeEach(async () => {
    orderServiceSpy = jasmine.createSpyObj('OrderAdminService', ['getOrders', 'updateOrderStatus']);
    orderServiceSpy.getOrders.and.returnValue(of(mockPaged));

    await TestBed.configureTestingModule({
      imports: [OrdersAdminPage, NoopAnimationsModule],
      providers: [
        { provide: OrderAdminService, useValue: orderServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersAdminPage);
    fixture.detectChanges();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Orders');
  });

  it('should call getOrders on init', () => {
    expect(orderServiceSpy.getOrders).toHaveBeenCalledWith({ page: 0, size: 10 });
  });

  it('should show status filter dropdown', () => {
    const filter = fixture.nativeElement.querySelector('[data-testid="status-filter"]');
    expect(filter).toBeTruthy();
  });

  it('should open drawer and set selectedOrder on row click', async () => {
    fixture.componentInstance.onRowClick(mockOrder as unknown as Record<string, unknown> & Order);
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedOrder()?.orderId).toBe('ord-1');
  });

  it('should display item lines in the drawer', async () => {
    fixture.componentInstance.onRowClick(mockOrder as unknown as Record<string, unknown> & Order);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Widget');
  });

  it('should close drawer and clear selectedOrder on closeDrawer', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.selectedOrder.set(mockOrder);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
    expect(fixture.componentInstance.selectedOrder()).toBeNull();
  });

  it('should call getOrders with status filter when onStatusFilter is invoked', () => {
    fixture.componentInstance.onStatusFilter('DELIVERED');
    expect(orderServiceSpy.getOrders).toHaveBeenCalledWith(jasmine.objectContaining({ status: 'DELIVERED', page: 0 }));
  });

  it('should call updateOrderStatus on status update', async () => {
    orderServiceSpy.updateOrderStatus.and.returnValue(of({ ...mockOrder, status: 'CONFIRMED' }));
    fixture.componentInstance.selectedOrder.set(mockOrder);
    fixture.componentInstance.newStatus.set('CONFIRMED');
    fixture.componentInstance.onUpdateStatus();
    await fixture.whenStable();
    expect(orderServiceSpy.updateOrderStatus).toHaveBeenCalledWith('ord-1', { status: 'CONFIRMED' });
  });

  it('should return correct order variant badge', () => {
    expect(fixture.componentInstance.orderVariant('PENDING')).toBe('warning');
    expect(fixture.componentInstance.orderVariant('DELIVERED')).toBe('success');
    expect(fixture.componentInstance.orderVariant('CANCELLED')).toBe('error');
  });
});
