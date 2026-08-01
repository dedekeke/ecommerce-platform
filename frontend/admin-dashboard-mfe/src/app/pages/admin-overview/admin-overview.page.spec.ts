import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminOverviewPage } from './admin-overview.page';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AnalyticsService } from '../../core/services/analytics.service';
import { of, throwError } from 'rxjs';
import { KpiSummary, ActivityEvent } from '../../core/models/analytics.model';

const mockKpi: KpiSummary = {
  todayOrders: 42,
  todayOrdersDelta: 5,
  todayRevenue: 3200,
  todayRevenueDelta: 8.5,
  lowStockItems: 7,
  pendingApprovals: 3,
};

const mockActivity: ActivityEvent[] = [
  { id: '1', type: 'ORDER_PLACED', description: 'New order #1234', timestamp: '2024-01-01T10:00:00Z' },
  { id: '2', type: 'USER_REGISTERED', description: 'New user registered', timestamp: '2024-01-01T09:00:00Z' },
];

describe('AdminOverviewPage', () => {
  let fixture: ComponentFixture<AdminOverviewPage>;
  let analyticsServiceSpy: jasmine.SpyObj<AnalyticsService>;

  beforeEach(async () => {
    analyticsServiceSpy = jasmine.createSpyObj('AnalyticsService', [
      'getKpiSummary',
      'getRecentActivity',
    ]);
    analyticsServiceSpy.getKpiSummary.and.returnValue(of(mockKpi));
    analyticsServiceSpy.getRecentActivity.and.returnValue(of(mockActivity));

    await TestBed.configureTestingModule({
      imports: [AdminOverviewPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: AnalyticsService, useValue: analyticsServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminOverviewPage);
    fixture.detectChanges();
  });

  it('should display the page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Admin Overview');
  });

  it('should display kpi grid with cards', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const grid = fixture.nativeElement.querySelector('[data-testid="kpi-grid"]');
    expect(grid).toBeTruthy();
    const cards = grid.querySelectorAll('app-kpi-card');
    expect(cards.length).toBe(4);
  });

  it('should display activity feed items', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('[data-testid="activity-item"]');
    expect(items.length).toBe(2);
  });

  it('should show quick action links', () => {
    const links = fixture.nativeElement.querySelectorAll('[data-testid="quick-link"]');
    expect(links.length).toBe(4);
  });

  it('should show empty activity message when no events', async () => {
    analyticsServiceSpy.getRecentActivity.and.returnValue(of([]));
    fixture = TestBed.createComponent(AdminOverviewPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No recent activity');
  });

  it('should handle kpi api error gracefully and stop loading', async () => {
    analyticsServiceSpy.getKpiSummary.and.returnValue(throwError(() => new Error('API error')));
    fixture = TestBed.createComponent(AdminOverviewPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.kpiLoading()).toBeFalse();
  });

  it('should return correct activity icon for each type', () => {
    const comp = fixture.componentInstance;
    expect(comp.activityIcon('ORDER_PLACED')).toBe('shopping_bag');
    expect(comp.activityIcon('USER_REGISTERED')).toBe('person_add');
    expect(comp.activityIcon('PAYMENT_FAILED')).toBe('error');
    expect(comp.activityIcon('ORDER_SHIPPED')).toBe('local_shipping');
    expect(comp.activityIcon('PRODUCT_UPDATED')).toBe('edit');
  });
});
