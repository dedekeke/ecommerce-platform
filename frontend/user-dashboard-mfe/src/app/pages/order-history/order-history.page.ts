import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../../core/services/order.service';
import { Order, OrderStatus, PagedOrders } from '../../core/models/order.model';
import { OrderCardComponent } from '../../shared/components/order-card/order-card.component';

const DEMO_USER_ID = 'me';
const PAGE_SIZE = 10;

@Component({
  selector: 'app-order-history-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    OrderCardComponent,
  ],
  template: `
    <div class="order-history-page container">
      <header class="order-history-page__header">
        <h1 class="order-history-page__title">Order History</h1>
        <mat-form-field appearance="outline" class="order-history-page__filter">
          <mat-label>Filter by status</mat-label>
          <mat-select [(ngModel)]="selectedStatus" (ngModelChange)="onStatusChange()">
            <mat-option [value]="null">All Orders</mat-option>
            <mat-option value="PENDING">Pending</mat-option>
            <mat-option value="CONFIRMED">Confirmed</mat-option>
            <mat-option value="PROCESSING">Processing</mat-option>
            <mat-option value="SHIPPED">Shipped</mat-option>
            <mat-option value="DELIVERED">Delivered</mat-option>
            <mat-option value="CANCELLED">Cancelled</mat-option>
          </mat-select>
        </mat-form-field>
      </header>

      @if (loading()) {
        <div class="order-history-page__loading" role="status" aria-label="Loading orders">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (orders().length === 0) {
        <div class="order-history-page__empty">
          <p>No orders found.</p>
        </div>
      } @else {
        <div class="orders-grid">
          @for (order of orders(); track order.id) {
            <app-order-card
              [order]="order"
              (viewDetails)="onViewOrder($event)"
            />
          }
        </div>

        @if (totalElements() > PAGE_SIZE) {
          <mat-paginator
            [length]="totalElements()"
            [pageSize]="PAGE_SIZE"
            [pageIndex]="currentPage()"
            (page)="onPageChange($event)"
            aria-label="Order list pagination"
          />
        }
      }
    </div>
  `,
  styleUrl: './order-history.page.scss',
})
export class OrderHistoryPage implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);

  readonly orders = signal<Order[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly currentPage = signal(0);
  readonly PAGE_SIZE = PAGE_SIZE;

  selectedStatus: OrderStatus | null = null;

  ngOnInit(): void {
    this.fetchOrders();
  }

  onStatusChange(): void {
    this.currentPage.set(0);
    this.fetchOrders();
  }

  onPageChange(event: PageEvent): void {
    this.currentPage.set(event.pageIndex);
    this.fetchOrders();
  }

  onViewOrder(orderId: string): void {
    this.router.navigate(['orders', orderId]);
  }

  private fetchOrders(): void {
    this.loading.set(true);
    this.orderService
      .getOrdersForUser(DEMO_USER_ID, {
        page: this.currentPage(),
        size: PAGE_SIZE,
        status: this.selectedStatus ?? undefined,
      })
      .subscribe({
        next: (paged: PagedOrders) => {
          this.orders.set(paged.content);
          this.totalElements.set(paged.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
