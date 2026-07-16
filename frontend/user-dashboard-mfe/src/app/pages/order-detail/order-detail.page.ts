import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { OrderService } from '../../core/services/order.service';
import { Order, TimelineEvent } from '../../core/models/order.model';
import { OrderTimelineComponent } from '../../shared/components/order-timeline/order-timeline.component';
import { buildOrderTimeline } from '../../core/utils/order-timeline.util';

@Component({
  selector: 'app-order-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    OrderTimelineComponent,
  ],
  template: `
    <div class="order-detail-page container">
      <a mat-button routerLink="/orders" class="order-detail-page__back" aria-label="Back to orders">
        <mat-icon>arrow_back</mat-icon>
        Back to Orders
      </a>

      @if (loading()) {
        <div class="order-detail-page__loading" role="status" aria-label="Loading order">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (order()) {
        <header class="order-detail-page__header">
          <div>
            <h1 class="order-detail-page__title">{{ order()!.orderNumber }}</h1>
            <time class="order-detail-page__date">{{ order()!.createdAt | date: 'longDate' }}</time>
          </div>
          <span class="status-badge status-badge--{{ order()!.status.toLowerCase() }}">
            {{ order()!.status }}
          </span>
        </header>

        <div class="order-detail-page__layout">
          <div class="order-detail-page__main">
            <section class="detail-card" aria-labelledby="items-heading">
              <h2 id="items-heading" class="detail-card__title">Items</h2>
              <ul class="line-items" role="list">
                @for (item of order()!.items; track item.productId) {
                  <li class="line-item">
                    <div class="line-item__info">
                      <span class="line-item__name">{{ item.productName }}</span>
                      <span class="line-item__qty">Qty: {{ item.quantity }}</span>
                    </div>
                    <span class="line-item__price">{{ item.subtotal | currency }}</span>
                  </li>
                }
              </ul>
            </section>

            @if (order()!.shippingAddress) {
              <section class="detail-card" aria-labelledby="shipping-heading">
                <h2 id="shipping-heading" class="detail-card__title">Shipping</h2>
                <dl class="detail-dl">
                  @if (order()!.carrier) {
                    <dt>Carrier</dt>
                    <dd>{{ order()!.carrier }}</dd>
                  }
                  @if (order()!.trackingNumber) {
                    <dt>Tracking</dt>
                    <dd>{{ order()!.trackingNumber }}</dd>
                  }
                  <dt>Address</dt>
                  <dd>
                    {{ order()!.shippingAddress!.street }},
                    {{ order()!.shippingAddress!.city }},
                    {{ order()!.shippingAddress!.state }}
                    {{ order()!.shippingAddress!.postalCode }}
                  </dd>
                </dl>
              </section>
            }
          </div>

          <aside class="order-detail-page__aside">
            <section class="detail-card" aria-labelledby="payment-heading">
              <h2 id="payment-heading" class="detail-card__title">Payment Summary</h2>
              <dl class="detail-dl detail-dl--summary">
                <dt>Subtotal</dt><dd>{{ order()!.subtotal | currency }}</dd>
                <dt>Shipping</dt><dd>{{ order()!.shippingCost | currency }}</dd>
                <dt>Tax</dt><dd>{{ order()!.tax | currency }}</dd>
                @if (order()!.discountAmount && order()!.discountAmount! > 0) {
                  <dt>Discount</dt><dd>-{{ order()!.discountAmount | currency }}</dd>
                }
                @if (order()!.loyaltyDiscount && order()!.loyaltyDiscount! > 0) {
                  <dt>Loyalty Discount</dt><dd>-{{ order()!.loyaltyDiscount | currency }}</dd>
                }
                <mat-divider />
                <dt class="detail-dl__total-label">Total</dt>
                <dd class="detail-dl__total-value">{{ order()!.total | currency }}</dd>
              </dl>
            </section>

            <section class="detail-card" aria-labelledby="timeline-heading">
              <h2 id="timeline-heading" class="detail-card__title">Status Timeline</h2>
              <app-order-timeline [timeline]="timeline()" />
            </section>
          </aside>
        </div>
      }
    </div>
  `,
  styleUrl: './order-detail.page.scss',
})
export class OrderDetailPage implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly route = inject(ActivatedRoute);

  readonly order = signal<Order | null>(null);
  readonly timeline = signal<TimelineEvent[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    if (orderId) {
      this.orderService.getOrderById(orderId).subscribe({
        next: (o) => {
          this.order.set(o);
          this.timeline.set(buildOrderTimeline(o));
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }
  }
}
