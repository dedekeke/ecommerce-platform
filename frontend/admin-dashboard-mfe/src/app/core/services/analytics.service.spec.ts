import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AnalyticsService } from './analytics.service';
import { RevenueDataPoint, CategoryRevenue, KpiSummary } from '../models/analytics.model';

const mockRevenue: RevenueDataPoint[] = [
  { date: '2024-01-01', revenue: 1200, orderCount: 12 },
  { date: '2024-01-02', revenue: 1800, orderCount: 18 },
];

const mockCategories: CategoryRevenue[] = [
  { category: 'Electronics', revenue: 5000, percentage: 50 },
  { category: 'Clothing', revenue: 5000, percentage: 50 },
];

const mockKpi: KpiSummary = {
  todayOrders: 42,
  todayOrdersDelta: 5,
  todayRevenue: 3200,
  todayRevenueDelta: 8.5,
  lowStockItems: 7,
  pendingApprovals: 3,
};

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch revenue data with days param', () => {
    let result: RevenueDataPoint[] | undefined;
    service.getRevenue(30).subscribe((r) => (result = r));
    const req = httpMock.expectOne((r) => r.url === '/api/analytics/revenue');
    expect(req.request.params.get('days')).toBe('30');
    req.flush(mockRevenue);
    expect(result).toEqual(mockRevenue);
  });

  it('should fetch top categories', () => {
    let result: CategoryRevenue[] | undefined;
    service.getTopCategories().subscribe((r) => (result = r));
    const req = httpMock.expectOne('/api/analytics/top-categories');
    req.flush(mockCategories);
    expect(result).toEqual(mockCategories);
  });

  it('should fetch kpi summary', () => {
    let result: KpiSummary | undefined;
    service.getKpiSummary().subscribe((r) => (result = r));
    const req = httpMock.expectOne('/api/analytics/kpi-summary');
    req.flush(mockKpi);
    expect(result).toEqual(mockKpi);
  });

  it('should return stub revenue data for the given number of days', (done) => {
    service.getRevenueStub(7).subscribe((data) => {
      expect(data.length).toBe(7);
      data.forEach((d) => {
        expect(d.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        expect(d.revenue).toBeGreaterThan(0);
      });
      done();
    });
  });

  it('should return stub top categories', (done) => {
    service.getTopCategoriesStub().subscribe((data) => {
      expect(data.length).toBeGreaterThan(0);
      expect(data[0].category).toBeTruthy();
      done();
    });
  });
});
