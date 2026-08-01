import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { RevenueDataPoint, CategoryRevenue, KpiSummary, ActivityEvent } from '../models/analytics.model';

// TODO (Backend): Implement the following endpoints:
//   GET /api/analytics/revenue?days=30  -> RevenueDataPoint[]
//   GET /api/analytics/top-categories   -> CategoryRevenue[]
//   GET /api/analytics/kpi-summary      -> KpiSummary
//   GET /api/analytics/recent-activity  -> ActivityEvent[]
// Stub data is returned here until those endpoints exist.

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly api = inject(ApiClientService);

  getRevenue(days = 30): Observable<RevenueDataPoint[]> {
    return this.api.get<RevenueDataPoint[]>('/analytics/revenue', { days });
  }

  getTopCategories(): Observable<CategoryRevenue[]> {
    return this.api.get<CategoryRevenue[]>('/analytics/top-categories');
  }

  getKpiSummary(): Observable<KpiSummary> {
    return this.api.get<KpiSummary>('/analytics/kpi-summary');
  }

  getRecentActivity(): Observable<ActivityEvent[]> {
    return this.api.get<ActivityEvent[]>('/analytics/recent-activity');
  }

  getRevenueStub(days = 30): Observable<RevenueDataPoint[]> {
    const now = new Date();
    const data: RevenueDataPoint[] = Array.from({ length: days }, (_, i) => {
      const d = new Date(now);
      d.setDate(d.getDate() - (days - 1 - i));
      return {
        date: d.toISOString().split('T')[0],
        revenue: Math.floor(Math.random() * 5000) + 500,
        orderCount: Math.floor(Math.random() * 50) + 5,
      };
    });
    return of(data);
  }

  getTopCategoriesStub(): Observable<CategoryRevenue[]> {
    return of([
      { category: 'Electronics', revenue: 45000, percentage: 35 },
      { category: 'Clothing', revenue: 28000, percentage: 22 },
      { category: 'Home & Garden', revenue: 19000, percentage: 15 },
      { category: 'Sports', revenue: 15000, percentage: 12 },
      { category: 'Books', revenue: 10000, percentage: 8 },
      { category: 'Other', revenue: 10000, percentage: 8 },
    ]);
  }
}
