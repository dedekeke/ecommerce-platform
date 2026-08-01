import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card.component';
import { AnalyticsService } from '../../core/services/analytics.service';
import { KpiSummary, ActivityEvent } from '../../core/models/analytics.model';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    KpiCardComponent,
  ],
  template: `
    <div class="overview-page container">
      <header class="overview-page__header">
        <h1 class="overview-page__title">Admin Overview</h1>
        <p class="overview-page__subtitle">Welcome back. Here's what's happening today.</p>
      </header>

      <section aria-labelledby="kpi-heading" class="overview-page__kpis">
        <h2 id="kpi-heading" class="sr-only">Key Performance Indicators</h2>
        @if (kpiLoading()) {
          <div class="kpi-grid">
            @for (n of [1,2,3,4]; track n) {
              <div class="skeleton skeleton--card"></div>
            }
          </div>
        } @else if (kpi()) {
          <div class="kpi-grid" data-testid="kpi-grid">
            <app-kpi-card
              title="Today's Orders"
              [value]="kpi()!.todayOrders"
              [delta]="kpi()!.todayOrdersDelta"
              icon="shopping_bag"
            />
            <app-kpi-card
              title="Today's Revenue"
              [value]="kpi()!.todayRevenue"
              [delta]="kpi()!.todayRevenueDelta"
              prefix="$"
              icon="payments"
            />
            <app-kpi-card
              title="Low Stock Items"
              [value]="kpi()!.lowStockItems"
              icon="inventory_2"
            />
            <app-kpi-card
              title="Pending Approvals"
              [value]="kpi()!.pendingApprovals"
              icon="pending_actions"
            />
          </div>
        }
      </section>

      <div class="overview-page__grid">
        <section class="overview-page__activity" aria-labelledby="activity-heading">
          <h2 id="activity-heading" class="overview-page__section-title">Recent Activity</h2>

          @if (activityLoading()) {
            @for (n of [1,2,3,4,5]; track n) {
              <div class="skeleton skeleton--row"></div>
            }
          } @else if (activity().length === 0) {
            <p class="overview-page__empty-text">No recent activity.</p>
          } @else {
            <ul class="activity-feed" aria-label="Recent activity feed">
              @for (event of activity(); track event.id) {
                <li class="activity-feed__item" data-testid="activity-item">
                  <mat-icon class="activity-feed__icon" [attr.aria-hidden]="true">
                    {{ activityIcon(event.type) }}
                  </mat-icon>
                  <div class="activity-feed__content">
                    <span class="activity-feed__description">{{ event.description }}</span>
                    <time class="activity-feed__time" [attr.datetime]="event.timestamp">
                      {{ event.timestamp | date: 'short' }}
                    </time>
                  </div>
                </li>
              }
            </ul>
          }
        </section>

        <nav class="overview-page__quick-links" aria-label="Admin quick links">
          <h2 class="overview-page__section-title">Quick Actions</h2>
          <div class="quick-links-grid">
            <a mat-stroked-button routerLink="/products" data-testid="quick-link">
              <mat-icon>inventory_2</mat-icon>
              Products
            </a>
            <a mat-stroked-button routerLink="/orders" data-testid="quick-link">
              <mat-icon>receipt_long</mat-icon>
              Orders
            </a>
            <a mat-stroked-button routerLink="/users" data-testid="quick-link">
              <mat-icon>people</mat-icon>
              Users
            </a>
            <a mat-stroked-button routerLink="/analytics" data-testid="quick-link">
              <mat-icon>bar_chart</mat-icon>
              Analytics
            </a>
          </div>
        </nav>
      </div>
    </div>
  `,
  styleUrl: './admin-overview.page.scss',
})
export class AdminOverviewPage implements OnInit {
  private readonly analytics = inject(AnalyticsService);

  readonly kpi = signal<KpiSummary | null>(null);
  readonly kpiLoading = signal(true);
  readonly activity = signal<ActivityEvent[]>([]);
  readonly activityLoading = signal(true);

  ngOnInit(): void {
    this.analytics.getKpiSummary().pipe(
      catchError(() => of(null))
    ).subscribe((data) => {
      this.kpi.set(data);
      this.kpiLoading.set(false);
    });

    this.analytics.getRecentActivity().pipe(
      catchError(() => of([]))
    ).subscribe((data) => {
      this.activity.set(data);
      this.activityLoading.set(false);
    });
  }

  activityIcon(type: ActivityEvent['type']): string {
    const map: Record<ActivityEvent['type'], string> = {
      ORDER_PLACED: 'shopping_bag',
      USER_REGISTERED: 'person_add',
      PRODUCT_UPDATED: 'edit',
      PAYMENT_FAILED: 'error',
      ORDER_SHIPPED: 'local_shipping',
    };
    return map[type] ?? 'info';
  }
}
