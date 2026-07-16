import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrdersAdminPage } from './orders-admin.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OrderAdminService } from '../../core/services/order-admin.service';
import { of } from 'rxjs';
import { AdminOrder, Order, PagedAdminOrders } from '../../core/models/order.model';

const mockAdminOrder: AdminOrder = {
  orderId: 'ord-1',
  orderNumber: 'ORD-0001',
  userId: 'auth0|user-a',
  guestEmail: null,
  guestOrder: false,
  status: 'PENDING',
  currency: 'USD',
  total: 115,
  itemCount: 1,
  items: [{ productId: 'p1', productName: 'Widget', price: 100, quantity: 1, subtotal: 100 }],
  carrier: 'FedEx',
  trackingNumber: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockGuestOrder: AdminOrder = {
  ...mockAdminOrder,
  orderId: 'ord-2',
  orderNumber: 'ORD-0002',
  userId: 'guest:guest@example.com',
  guestEmail: 'guest@example.com',
  guestOrder: true,
};

const mockPaged: PagedAdminOrders = {
  content: [mockAdminOrder],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

// updateOrderStatus returns the customer-facing OrderResponse, not AdminOrder.
const mockOrderResponse: Order = {
  orderId: 'ord-1',
  orderNumber: 'ORD-0001',
  status: 'CONFIRMED',
  currency: 'USD',
  subtotal: 100,
  tax: 5,
  shippingCost: 10,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 115,
  items: [{ productId: 'p1', productName: 'Widget', price: 100, quantity: 1, subtotal: 100 }],
  shippingAddress: null,
  paymentIntentId: null,
  guestOrder: false,
  carrier: 'FedEx',
  trackingNumber: null,
  shippedAt: null,
  deliveredAt: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
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

  it('should render the loaded orders and total', () => {
    expect(fixture.componentInstance.orders().length).toBe(1);
    expect(fixture.componentInstance.totalElements()).toBe(1);
  });

  it('should show status filter dropdown', () => {
    const filter = fixture.nativeElement.querySelector('[data-testid="status-filter"]');
    expect(filter).toBeTruthy();
  });

  it('should open drawer and set selectedOrder on row click', () => {
    fixture.componentInstance.onRowClick(mockAdminOrder as unknown as Record<string, unknown> & AdminOrder);
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedOrder()?.orderId).toBe('ord-1');
  });

  it('should show the customer identity for a registered order in the drawer', () => {
    fixture.componentInstance.onRowClick(mockAdminOrder as unknown as Record<string, unknown> & AdminOrder);
    fixture.detectChanges();
    const identity = fixture.nativeElement.querySelector('[data-testid="customer-identity"]');
    expect(identity.textContent).toContain('auth0|user-a');
  });

  it('should show the guest email as customer identity for a guest order', () => {
    expect(fixture.componentInstance.customerLabel(mockGuestOrder)).toBe('guest@example.com');
    expect(fixture.componentInstance.customerLabel(mockAdminOrder)).toBe('auth0|user-a');
  });

  it('should display item lines in the drawer', () => {
    fixture.componentInstance.onRowClick(mockAdminOrder as unknown as Record<string, unknown> & AdminOrder);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Widget');
  });

  it('should close drawer and clear selectedOrder on closeDrawer', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.selectedOrder.set(mockAdminOrder);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
    expect(fixture.componentInstance.selectedOrder()).toBeNull();
  });

  it('should call getOrders with status filter when onStatusFilter is invoked', () => {
    fixture.componentInstance.onStatusFilter('DELIVERED');
    expect(orderServiceSpy.getOrders).toHaveBeenCalledWith(jasmine.objectContaining({ status: 'DELIVERED', page: 0 }));
  });

  it('should call updateOrderStatus on status update and reflect the new status', async () => {
    orderServiceSpy.updateOrderStatus.and.returnValue(of(mockOrderResponse));
    fixture.componentInstance.selectedOrder.set(mockAdminOrder);
    fixture.componentInstance.newStatus.set('CONFIRMED');
    fixture.componentInstance.onUpdateStatus();
    await fixture.whenStable();
    expect(orderServiceSpy.updateOrderStatus).toHaveBeenCalledWith('ord-1', { status: 'CONFIRMED' });
    expect(fixture.componentInstance.selectedOrder()?.status).toBe('CONFIRMED');
  });

  it('should return correct order variant badge', () => {
    expect(fixture.componentInstance.orderVariant('PENDING')).toBe('warning');
    expect(fixture.componentInstance.orderVariant('DELIVERED')).toBe('success');
    expect(fixture.componentInstance.orderVariant('CANCELLED')).toBe('error');
  });
});
