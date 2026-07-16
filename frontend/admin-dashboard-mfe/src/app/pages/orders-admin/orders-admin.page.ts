import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { OrderAdminService } from '../../core/services/order-admin.service';
import { AdminOrder, OrderStatus, OrderFilterParams } from '../../core/models/order.model';

type OrderRow = Record<string, unknown> & AdminOrder;

const ORDER_STATUSES: OrderStatus[] = [
  'PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED',
];

@Component({
  selector: 'app-orders-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatSnackBarModule,
    DataTableComponent,
    StatusBadgeComponent,
    FormDrawerComponent,
  ],
  template: `
    <div class="orders-admin container">
      <header class="orders-admin__header">
        <h1>Orders</h1>

        <mat-form-field appearance="outline" class="orders-admin__filter" subscriptSizing="dynamic">
          <mat-label>Filter by Status</mat-label>
          <mat-select [value]="statusFilter()" (valueChange)="onStatusFilter($event)" data-testid="status-filter">
            <mat-option value="">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </header>

      <app-data-table
        [columns]="columns"
        [rows]="orders()"
        [loading]="loading()"
        [totalElements]="totalElements()"
        [pageSize]="pageSize()"
        [pageIndex]="pageIndex()"
        emptyMessage="No orders found."
        ariaLabel="Orders table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (pageChange)="onPage($event)"
        (rowClick)="onRowClick($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="orderVariant(row['status'])" />
        } @else if (col.key === 'total') {
          {{ row['total'] | currency }}
        } @else if (col.key === 'createdAt') {
          {{ row['createdAt'] | date: 'mediumDate' }}
        } @else if (col.key === 'itemCount') {
          {{ row['itemCount'] ?? row['items']?.length ?? 0 }}
        } @else if (col.key === 'customer') {
          {{ customerLabel(row) }}
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <app-form-drawer
        [title]="'Order ' + (selectedOrder()?.orderNumber ?? '')"
        [open]="drawerOpen()"
        (drawerClose)="closeDrawer()"
      >
        @if (selectedOrder()) {
          <div class="order-detail">
            <div class="order-detail__row">
              <span class="order-detail__label">Order Type</span>
              <span>{{ selectedOrder()!.guestOrder ? 'Guest' : 'Registered customer' }}</span>
            </div>
            <div class="order-detail__row">
              <span class="order-detail__label">Customer</span>
              <span data-testid="customer-identity">{{ customerLabel(selectedOrder()!) }}</span>
            </div>
            <div class="order-detail__row">
              <span class="order-detail__label">Total</span>
              <span>{{ selectedOrder()!.total | currency }}</span>
            </div>
            <div class="order-detail__row">
              <span class="order-detail__label">Status</span>
              <app-status-badge
                [label]="selectedOrder()!.status"
                [variant]="orderVariant(selectedOrder()!.status)"
              />
            </div>
            @if (selectedOrder()!.carrier) {
              <div class="order-detail__row">
                <span class="order-detail__label">Carrier</span>
                <span>{{ selectedOrder()!.carrier }}</span>
              </div>
            }
            @if (selectedOrder()!.trackingNumber) {
              <div class="order-detail__row">
                <span class="order-detail__label">Tracking</span>
                <span>{{ selectedOrder()!.trackingNumber }}</span>
              </div>
            }

            <h3 class="order-detail__section">Items</h3>
            <ul class="order-detail__items">
              @for (item of selectedOrder()!.items; track item.productId) {
                <li>{{ item.productName }} × {{ item.quantity }} — {{ item.subtotal | currency }}</li>
              }
            </ul>

            <h3 class="order-detail__section">Update Status</h3>
            <mat-form-field appearance="outline">
              <mat-label>New Status</mat-label>
              <mat-select [value]="newStatus()" (valueChange)="newStatus.set($event)" data-testid="new-status-select">
                @for (s of statuses; track s) {
                  <mat-option [value]="s">{{ s }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <button
              mat-flat-button
              color="primary"
              (click)="onUpdateStatus()"
              data-testid="update-status-btn"
              [disabled]="newStatus() === selectedOrder()!.status || updatingStatus()"
            >
              {{ updatingStatus() ? 'Updating...' : 'Update Status' }}
            </button>
          </div>
        }
      </app-form-drawer>
    </div>
  `,
  styleUrl: './orders-admin.page.scss',
})
export class OrdersAdminPage implements OnInit {
  private readonly orderService = inject(OrderAdminService);
  private readonly snackBar = inject(MatSnackBar);

  readonly orders = signal<OrderRow[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly pageSize = signal(10);
  readonly pageIndex = signal(0);
  readonly statusFilter = signal<OrderStatus | ''>('');
  readonly drawerOpen = signal(false);
  readonly selectedOrder = signal<AdminOrder | null>(null);
  readonly newStatus = signal<OrderStatus>('PENDING');
  readonly updatingStatus = signal(false);

  readonly statuses = ORDER_STATUSES;

  readonly columns: TableColumn[] = [
    { key: 'orderNumber', label: 'Order #' },
    { key: 'customer', label: 'Customer' },
    { key: 'createdAt', label: 'Date' },
    { key: 'itemCount', label: 'Items' },
    { key: 'status', label: 'Status' },
    { key: 'total', label: 'Total' },
  ];

  private currentParams: OrderFilterParams = { page: 0, size: 10 };

  ngOnInit(): void {
    this.loadOrders();
  }

  private loadOrders(): void {
    this.loading.set(true);
    const status = this.statusFilter();
    const params: OrderFilterParams = {
      ...this.currentParams,
      ...(status ? { status } : {}),
    };
    this.orderService.getOrders(params).subscribe({
      next: (paged) => {
        this.orders.set(paged.content as OrderRow[]);
        this.totalElements.set(paged.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onStatusFilter(status: OrderStatus | ''): void {
    this.statusFilter.set(status);
    this.currentParams = { ...this.currentParams, page: 0 };
    this.pageIndex.set(0);
    this.loadOrders();
  }

  onPage(event: PageEvent): void {
    this.currentParams = { ...this.currentParams, page: event.pageIndex, size: event.pageSize };
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadOrders();
  }

  onRowClick(row: OrderRow): void {
    this.selectedOrder.set(row as unknown as AdminOrder);
    this.newStatus.set(row['status'] as OrderStatus);
    this.drawerOpen.set(true);
  }

  /** Human-readable customer identity: guest email for guest orders, else the userId. */
  customerLabel(order: AdminOrder | Record<string, unknown>): string {
    const guest = order['guestOrder'] as boolean;
    const guestEmail = order['guestEmail'] as string | null;
    const userId = order['userId'] as string | null;
    if (guest) {
      return guestEmail ?? 'Guest';
    }
    return userId ?? '—';
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
    this.selectedOrder.set(null);
  }

  onUpdateStatus(): void {
    const order = this.selectedOrder();
    if (!order) return;
    this.updatingStatus.set(true);
    this.orderService.updateOrderStatus(order.orderId, { status: this.newStatus() }).subscribe({
      next: (updated) => {
        this.updatingStatus.set(false);
        // updateOrderStatus returns the customer-facing OrderResponse; reflect
        // only the new status onto the admin view we already hold.
        this.selectedOrder.set({ ...order, status: updated.status });
        this.snackBar.open('Status updated', 'Close', { duration: 3000 });
        this.loadOrders();
      },
      error: () => {
        this.updatingStatus.set(false);
        this.snackBar.open('Failed to update status', 'Close', { duration: 3000 });
      },
    });
  }

  orderVariant(status: string): BadgeVariant {
    const map: Record<OrderStatus, BadgeVariant> = {
      PENDING: 'warning',
      CONFIRMED: 'info',
      PROCESSING: 'info',
      SHIPPED: 'info',
      DELIVERED: 'success',
      CANCELLED: 'error',
      REFUNDED: 'error',
    };
    return map[status as OrderStatus] ?? 'neutral';
  }
}
