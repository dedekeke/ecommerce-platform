import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnalyticsPage } from './analytics.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AnalyticsService } from '../../core/services/analytics.service';
import { of } from 'rxjs';
import { RevenueDataPoint, CategoryRevenue } from '../../core/models/analytics.model';

const mockRevenue: RevenueDataPoint[] = Array.from({ length: 30 }, (_, i) => ({
  date: `2024-01-${String(i + 1).padStart(2, '0')}`,
  revenue: 1000 + i * 50,
  orderCount: 10 + i,
}));

const mockCategories: CategoryRevenue[] = [
  { category: 'Electronics', revenue: 5000, percentage: 50 },
  { category: 'Clothing', revenue: 5000, percentage: 50 },
];

describe('AnalyticsPage', () => {
  let fixture: ComponentFixture<AnalyticsPage>;
  let analyticsServiceSpy: jasmine.SpyObj<AnalyticsService>;

  beforeEach(async () => {
    analyticsServiceSpy = jasmine.createSpyObj('AnalyticsService', [
      'getRevenueStub', 'getTopCategoriesStub',
    ]);
    analyticsServiceSpy.getRevenueStub.and.returnValue(of(mockRevenue));
    analyticsServiceSpy.getTopCategoriesStub.and.returnValue(of(mockCategories));

    await TestBed.configureTestingModule({
      imports: [AnalyticsPage, NoopAnimationsModule],
      providers: [
        { provide: AnalyticsService, useValue: analyticsServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsPage);
    fixture.detectChanges();
  });

  it('should display the analytics heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Analytics');
  });

  it('should call getRevenueStub on init', () => {
    expect(analyticsServiceSpy.getRevenueStub).toHaveBeenCalledWith(30);
  });

  it('should call getTopCategoriesStub on init', () => {
    expect(analyticsServiceSpy.getTopCategoriesStub).toHaveBeenCalled();
  });

  it('should set revenueLoading to false after data is loaded', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.revenueLoading()).toBeFalse();
  });

  it('should set categoryLoading to false after data is loaded', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.categoryLoading()).toBeFalse();
  });

  it('should populate revenue chart data with correct labels and values', async () => {
    await fixture.whenStable();
    const chartData = fixture.componentInstance.revenueChartData();
    expect(chartData.labels?.length).toBe(30);
    expect((chartData.datasets[0].data as number[]).length).toBe(30);
  });

  it('should populate category chart data', async () => {
    await fixture.whenStable();
    const chartData = fixture.componentInstance.categoryChartData();
    expect(chartData.labels?.length).toBe(2);
    expect((chartData.datasets[0].data as number[])[0]).toBe(5000);
  });
});
