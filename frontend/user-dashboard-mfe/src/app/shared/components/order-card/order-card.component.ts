import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { Order, OrderStatus } from '../../../core/models/order.model';

@Component({
  selector: 'app-order-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatChipsModule],
  template: `
    <mat-card class="order-card" [class]="'status-' + order().status.toLowerCase()">
      <mat-card-content>
        <div class="order-card__header">
          <div class="order-card__number">
            <span class="order-card__label">Order</span>
            <span class="order-card__value">{{ order().orderNumber }}</span>
          </div>
          <span class="order-card__badge" [class]="statusClass(order().status)">
            {{ order().status }}
          </span>
        </div>

        <div class="order-card__meta">
          <span class="order-card__date">{{ order().createdAt | date: 'mediumDate' }}</span>
          <span class="order-card__items">{{ order().lineItems.length }} item{{ order().lineItems.length !== 1 ? 's' : '' }}</span>
        </div>

        <div class="order-card__total">
          <span class="order-card__total-label">Total</span>
          <span class="order-card__total-value">{{ order().payment.total | currency }}</span>
        </div>
      </mat-card-content>

      <mat-card-actions>
        <button
          mat-stroked-button
          color="primary"
          data-testid="view-details-btn"
          (click)="viewDetails.emit(order().id)"
          [attr.aria-label]="'View order details for ' + order().orderNumber"
        >
          View Details
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styleUrl: './order-card.component.scss',
})
export class OrderCardComponent {
  readonly order = input.required<Order>();
  readonly viewDetails = output<string>();

  statusClass(status: OrderStatus): string {
    const map: Record<OrderStatus, string> = {
      PENDING: 'badge--warning',
      CONFIRMED: 'badge--info',
      PROCESSING: 'badge--info',
      SHIPPED: 'badge--info',
      DELIVERED: 'badge--success',
      CANCELLED: 'badge--error',
      REFUNDED: 'badge--error',
    };
    return `order-card__badge ${map[status] ?? ''}`;
  }
}
