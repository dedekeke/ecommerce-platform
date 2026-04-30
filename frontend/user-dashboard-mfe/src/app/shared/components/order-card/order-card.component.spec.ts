import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderCardComponent } from './order-card.component';
import { Order } from '../../../core/models/order.model';

const mockOrder: Order = {
  id: 'order-1',
  orderNumber: 'ORD-001',
  userId: 'user-1',
  status: 'DELIVERED',
  lineItems: [
    {
      id: 'li-1',
      productId: 'prod-1',
      productName: 'Test Product',
      quantity: 2,
      unitPrice: 25.0,
      totalPrice: 50.0,
      sku: 'SKU-001',
    },
  ],
  shipping: {
    carrier: 'FedEx',
    address: { street: '123 Main St', city: 'NYC', state: 'NY', postalCode: '10001', country: 'US' },
  },
  payment: {
    method: 'CREDIT_CARD',
    last4: '4242',
    subtotal: 50.0,
    shippingCost: 5.0,
    tax: 4.5,
    discount: 0,
    total: 59.5,
  },
  timeline: [{ status: 'DELIVERED', timestamp: '2024-01-15T10:00:00Z' }],
  createdAt: '2024-01-10T10:00:00Z',
  updatedAt: '2024-01-15T10:00:00Z',
};

describe('OrderCardComponent', () => {
  let fixture: ComponentFixture<OrderCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderCardComponent);
    fixture.componentRef.setInput('order', mockOrder);
    fixture.detectChanges();
  });

  it('should render the order number', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('ORD-001');
  });

  it('should render the order total', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('59.5');
  });

  it('should render the status badge', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('DELIVERED');
  });

  it('should emit viewDetails when the card is clicked', () => {
    let emittedOrderId: string | undefined;
    fixture.componentInstance.viewDetails.subscribe((id: string) => (emittedOrderId = id));

    const button = fixture.nativeElement.querySelector('[data-testid="view-details-btn"]');
    button?.click();
    expect(emittedOrderId).toBe('order-1');
  });

  it('should display item count', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('1');
  });
});
