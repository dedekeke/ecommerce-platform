import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { UserService } from '../../core/services/user.service';
import { OrderService } from '../../core/services/order.service';
import { UserProfile } from '../../core/models/user.model';
import { Order } from '../../core/models/order.model';
import { OrderCardComponent } from '../../shared/components/order-card/order-card.component';

const DEMO_USER_ID = 'me';

@Component({
  selector: 'app-dashboard-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    OrderCardComponent,
  ],
  template: `
    <div class="overview-page container">
      <header class="overview-page__header">
        @if (profile()) {
          <h1 class="overview-page__greeting">
            Hey, {{ profile()!.firstName }}! 👋
          </h1>
          <p class="overview-page__subtitle">Here's what's happening with your account.</p>
        } @else {
          <div class="skeleton skeleton--heading"></div>
          <div class="skeleton skeleton--text"></div>
        }
      </header>

      <nav class="overview-page__quick-links" aria-label="Quick links">
        <a
          mat-stroked-button
          routerLink="profile"
          data-testid="quick-link"
          class="quick-link"
        >
          <mat-icon>person</mat-icon>
          Profile
        </a>
        <a
          mat-stroked-button
          routerLink="orders"
          data-testid="quick-link"
          class="quick-link"
        >
          <mat-icon>receipt_long</mat-icon>
          Orders
        </a>
        <a
          mat-stroked-button
          routerLink="addresses"
          data-testid="quick-link"
          class="quick-link"
        >
          <mat-icon>location_on</mat-icon>
          Addresses
        </a>
        <a
          mat-stroked-button
          routerLink="wishlist"
          data-testid="quick-link"
          class="quick-link"
        >
          <mat-icon>favorite</mat-icon>
          Wishlist
        </a>
      </nav>

      <section class="overview-page__section" aria-labelledby="recent-orders-heading">
        <h2 id="recent-orders-heading" class="overview-page__section-title">Recent Orders</h2>

        @if (loading()) {
          <div class="orders-grid">
            @for (n of [1, 2, 3]; track n) {
              <div class="skeleton skeleton--card"></div>
            }
          </div>
        } @else if (recentOrders().length === 0) {
          <div class="overview-page__empty">
            <mat-icon class="overview-page__empty-icon">receipt_long</mat-icon>
            <p>No recent orders. Start shopping!</p>
            <a mat-flat-button color="primary" href="/catalog">Browse Products</a>
          </div>
        } @else {
          <div class="orders-grid">
            @for (order of recentOrders(); track order.id) {
              <app-order-card
                [order]="order"
                (viewDetails)="onViewOrder($event)"
              />
            }
          </div>
        }
      </section>
    </div>
  `,
  styleUrl: './dashboard-overview.page.scss',
})
export class DashboardOverviewPage implements OnInit {
  private readonly userService = inject(UserService);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);

  readonly profile = signal<UserProfile | null>(null);
  readonly recentOrders = signal<Order[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.userService.getProfile(DEMO_USER_ID).subscribe({
      next: (profile) => this.profile.set(profile),
    });

    this.orderService.getOrdersForUser(DEMO_USER_ID, { page: 0, size: 3 }).subscribe({
      next: (paged) => {
        this.recentOrders.set(paged.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onViewOrder(orderId: string): void {
    this.router.navigate(['/dashboard/orders', orderId]);
  }
}
