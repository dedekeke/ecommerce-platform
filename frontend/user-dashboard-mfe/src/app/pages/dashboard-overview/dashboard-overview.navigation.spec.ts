import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardOverviewPage } from './dashboard-overview.page';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserService } from '../../core/services/user.service';
import { OrderService } from '../../core/services/order.service';
import { of } from 'rxjs';
import { UserProfile } from '../../core/models/user.model';
import { Order, PagedOrders } from '../../core/models/order.model';

const mockProfile: UserProfile = {
  id: 'user-1',
  firstName: 'Bob',
  lastName: 'Jones',
  email: 'bob@example.com',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockOrder: Order = {
  id: 'order-dash-1',
  orderNumber: 'ORD-DASH-001',
  userId: 'user-1',
  status: 'CONFIRMED',
  lineItems: [],
  shipping: {
    carrier: 'DHL',
    address: { street: '3 Oak Ave', city: 'Chicago', state: 'IL', postalCode: '60601', country: 'US' },
  },
  payment: { method: 'CARD', subtotal: 45, shippingCost: 5, tax: 4, discount: 0, total: 54 },
  timeline: [{ status: 'CONFIRMED', timestamp: '2024-03-01T10:00:00Z' }],
  createdAt: '2024-03-01T00:00:00Z',
  updatedAt: '2024-03-01T00:00:00Z',
};

const mockPagedWithOrders: PagedOrders = {
  content: [mockOrder],
  totalElements: 1,
  totalPages: 1,
  size: 3,
  number: 0,
};

describe('DashboardOverviewPage — navigation', () => {
  let fixture: ComponentFixture<DashboardOverviewPage>;
  let component: DashboardOverviewPage;

  beforeEach(async () => {
    const userServiceSpy = jasmine.createSpyObj('UserService', ['getProfile']);
    const orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrdersForUser']);
    userServiceSpy.getProfile.and.returnValue(of(mockProfile));
    orderServiceSpy.getOrdersForUser.and.returnValue(of(mockPagedWithOrders));

    await TestBed.configureTestingModule({
      imports: [DashboardOverviewPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserService, useValue: userServiceSpy },
        { provide: OrderService, useValue: orderServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardOverviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should navigate to the order detail route on onViewOrder', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    component.onViewOrder('order-dash-1');

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard/orders', 'order-dash-1']);
  });

  it('should display recent orders when orders are returned', async () => {
    const cards = fixture.nativeElement.querySelectorAll('app-order-card');
    expect(cards.length).toBe(1);
  });

  it('should set loading to false after orders are fetched', () => {
    expect(component.loading()).toBeFalse();
  });

  it('should greet with the user first name', () => {
    expect(fixture.nativeElement.textContent).toContain('Bob');
  });
});
