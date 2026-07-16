import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { RefundsAdminPage } from './refunds-admin.page';
import { RefundAdminService } from '../../core/services/refund-admin.service';
import { OrderAdminService } from '../../core/services/order-admin.service';
import { PagedRefunds, RefundSagaState } from '../../core/models/refund.model';
import { Order } from '../../core/models/order.model';

const mockOrder: Order = {
  orderId: 'ord-1',
  orderNumber: 'ORD-0001',
  status: 'DELIVERED',
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

const mockSaga: RefundSagaState = {
  id: 'saga-1',
  orderId: 'ord-1',
  status: 'PENDING',
  currentStep: 'VALIDATE',
  refundAmount: 115,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const emptyPaged: PagedRefunds = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 };

describe('RefundsAdminPage', () => {
  let fixture: ComponentFixture<RefundsAdminPage>;
  let refundServiceSpy: jasmine.SpyObj<RefundAdminService>;
  let orderServiceSpy: jasmine.SpyObj<OrderAdminService>;

  beforeEach(async () => {
    refundServiceSpy = jasmine.createSpyObj('RefundAdminService', ['startRefund', 'getRefundSaga', 'getRefunds']);
    orderServiceSpy = jasmine.createSpyObj('OrderAdminService', ['getOrderById']);
    refundServiceSpy.getRefunds.and.returnValue(of(emptyPaged));

    await TestBed.configureTestingModule({
      imports: [RefundsAdminPage, NoopAnimationsModule],
      providers: [
        { provide: RefundAdminService, useValue: refundServiceSpy },
        { provide: OrderAdminService, useValue: orderServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RefundsAdminPage);
    fixture.detectChanges();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Refunds');
  });

  it('should call getRefunds on init', () => {
    expect(refundServiceSpy.getRefunds).toHaveBeenCalledWith({ page: 0, size: 20 });
  });

  it('should show the status filter dropdown', () => {
    const filter = fixture.nativeElement.querySelector('[data-testid="refund-status-filter"]');
    expect(filter).toBeTruthy();
  });

  it('should call getRefunds with status filter when onStatusFilter is invoked', () => {
    fixture.componentInstance.onStatusFilter('COMPLETED');
    expect(refundServiceSpy.getRefunds).toHaveBeenCalledWith(jasmine.objectContaining({ status: 'COMPLETED', page: 0 }));
  });

  it('should call getRefunds with new page params when onPage is invoked', () => {
    fixture.componentInstance.onPage({ pageIndex: 1, pageSize: 25, length: 100 });
    expect(refundServiceSpy.getRefunds).toHaveBeenCalledWith(jasmine.objectContaining({ page: 1, size: 25 }));
  });

  it('should show the empty state when no refund sagas exist yet', () => {
    const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(empty.textContent).toContain('No refunds initiated yet.');
  });

  it('should look up an order by id and render its summary', () => {
    orderServiceSpy.getOrderById.and.returnValue(of(mockOrder));
    fixture.componentInstance.lookupForm.setValue({ orderId: 'ord-1' });
    fixture.componentInstance.onLookupOrder();
    fixture.detectChanges();

    expect(orderServiceSpy.getOrderById).toHaveBeenCalledWith('ord-1');
    expect(fixture.componentInstance.lookedUpOrder()?.orderNumber).toBe('ORD-0001');
    const card = fixture.nativeElement.querySelector('[data-testid="order-card"]');
    expect(card).toBeTruthy();
  });

  it('should show an inline error when the order lookup fails', () => {
    orderServiceSpy.getOrderById.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.lookupForm.setValue({ orderId: 'missing' });
    fixture.componentInstance.onLookupOrder();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="lookup-error"]');
    expect(error.textContent).toContain('Order not found: missing');
    expect(fixture.componentInstance.lookedUpOrder()).toBeNull();
  });

  it('should show a non-refundable hint for a CANCELLED order instead of the refund form', () => {
    orderServiceSpy.getOrderById.and.returnValue(of({ ...mockOrder, status: 'CANCELLED' }));
    fixture.componentInstance.lookupForm.setValue({ orderId: 'ord-1' });
    fixture.componentInstance.onLookupOrder();
    fixture.detectChanges();

    const hint = fixture.nativeElement.querySelector('[data-testid="non-refundable-hint"]');
    expect(hint).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="initiate-refund-btn"]')).toBeFalsy();
  });

  it('should initiate a refund for a refundable order and reload the sagas table', () => {
    orderServiceSpy.getOrderById.and.returnValue(of(mockOrder));
    refundServiceSpy.startRefund.and.returnValue(of(mockSaga));
    refundServiceSpy.getRefunds.and.returnValue(of({ ...emptyPaged, content: [mockSaga], totalElements: 1 }));

    fixture.componentInstance.lookupForm.setValue({ orderId: 'ord-1' });
    fixture.componentInstance.onLookupOrder();
    fixture.componentInstance.refundForm.setValue({ reason: 'Customer request' });
    fixture.componentInstance.onInitiateRefund();
    fixture.detectChanges();

    expect(refundServiceSpy.startRefund).toHaveBeenCalledWith('ord-1', { reason: 'Customer request' });
    expect(fixture.componentInstance.refundSagas().length).toBe(1);
    expect(fixture.componentInstance.refundSagas()[0].id).toBe('saga-1');
  });

  it('should show an error snackbar-triggering path when initiate refund fails', () => {
    orderServiceSpy.getOrderById.and.returnValue(of(mockOrder));
    refundServiceSpy.startRefund.and.returnValue(throwError(() => new Error('500')));

    fixture.componentInstance.lookupForm.setValue({ orderId: 'ord-1' });
    fixture.componentInstance.onLookupOrder();
    fixture.componentInstance.onInitiateRefund();

    expect(fixture.componentInstance.initiatingRefund()).toBeFalse();
    expect(fixture.componentInstance.refundSagas().length).toBe(0);
  });

  it('should look up a refund saga by id and open its detail drawer', () => {
    refundServiceSpy.getRefundSaga.and.returnValue(of(mockSaga));
    fixture.componentInstance.sagaLookupForm.setValue({ sagaId: 'saga-1' });
    fixture.componentInstance.onLookupSaga();
    fixture.detectChanges();

    expect(refundServiceSpy.getRefundSaga).toHaveBeenCalledWith('saga-1');
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedSaga()?.id).toBe('saga-1');
  });

  it('should show an error snackbar path when the saga lookup fails', () => {
    refundServiceSpy.getRefundSaga.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.sagaLookupForm.setValue({ sagaId: 'missing' });
    fixture.componentInstance.onLookupSaga();

    expect(fixture.componentInstance.sagaLookupLoading()).toBeFalse();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
  });

  it('should open drawer and set selectedSaga on row click', () => {
    fixture.componentInstance.onRowClick(mockSaga as unknown as Record<string, unknown> & RefundSagaState);
    fixture.detectChanges();

    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.selectedSaga()?.id).toBe('saga-1');
  });

  it('should refresh saga status from the drawer', () => {
    const updated: RefundSagaState = { ...mockSaga, status: 'COMPLETED' };
    refundServiceSpy.getRefundSaga.and.returnValue(of(updated));

    fixture.componentInstance.onRowClick(mockSaga as unknown as Record<string, unknown> & RefundSagaState);
    fixture.componentInstance.onRefreshSaga('saga-1');
    fixture.detectChanges();

    expect(refundServiceSpy.getRefundSaga).toHaveBeenCalledWith('saga-1');
    expect(fixture.componentInstance.selectedSaga()?.status).toBe('COMPLETED');
  });

  it('should close the drawer and clear the selected saga', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.selectedSaga.set(mockSaga);
    fixture.componentInstance.closeDrawer();

    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
    expect(fixture.componentInstance.selectedSaga()).toBeNull();
  });

  it('should map saga statuses to the correct badge variants', () => {
    expect(fixture.componentInstance.sagaVariant('COMPLETED')).toBe('success');
    expect(fixture.componentInstance.sagaVariant('FAILED')).toBe('error');
    expect(fixture.componentInstance.sagaVariant('IN_PROGRESS')).toBe('info');
  });
});
