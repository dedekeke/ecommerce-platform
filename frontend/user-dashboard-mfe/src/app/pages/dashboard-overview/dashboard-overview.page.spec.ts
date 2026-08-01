import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardOverviewPage } from './dashboard-overview.page';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserService } from '../../core/services/user.service';
import { OrderService } from '../../core/services/order.service';
import { of } from 'rxjs';
import { UserProfile } from '../../core/models/user.model';
import { PagedOrders } from '../../core/models/order.model';

const mockProfile: UserProfile = {
  id: 'user-1',
  firstName: 'Alice',
  lastName: 'Smith',
  email: 'alice@example.com',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockPagedOrders: PagedOrders = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 5,
  number: 0,
};

describe('DashboardOverviewPage', () => {
  let fixture: ComponentFixture<DashboardOverviewPage>;
  let userServiceSpy: jasmine.SpyObj<UserService>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    userServiceSpy = jasmine.createSpyObj('UserService', ['getProfile']);
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrdersForUser']);
    userServiceSpy.getProfile.and.returnValue(of(mockProfile));
    orderServiceSpy.getOrdersForUser.and.returnValue(of(mockPagedOrders));

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
    fixture.detectChanges();
  });

  it('should display a greeting with the user name', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alice');
  });

  it('should show quick links navigation', () => {
    const links = fixture.nativeElement.querySelectorAll('[data-testid="quick-link"]');
    expect(links.length).toBeGreaterThan(0);
  });

  it('should show empty orders message when no orders exist', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No recent orders');
  });
});
