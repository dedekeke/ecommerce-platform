import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PageEvent } from '@angular/material/paginator';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { RefundAdminService } from '../../core/services/refund-admin.service';
import { OrderAdminService } from '../../core/services/order-admin.service';
import { ToastService } from '../../core/services/toast.service';
import { RefundFilterParams, RefundSagaState, RefundSagaStatus } from '../../core/models/refund.model';
import { Order, OrderStatus } from '../../core/models/order.model';

type RefundSagaRow = Record<string, unknown> & RefundSagaState;

const NON_REFUNDABLE_STATUSES: OrderStatus[] = ['PENDING', 'CANCELLED', 'REFUNDED'];

const REFUND_SAGA_STATUSES: RefundSagaStatus[] = [
  'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPENSATING', 'FAILED', 'COMPENSATED',
];

@Component({
  selector: 'app-refunds-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    DataTableComponent,
    StatusBadgeComponent,
    FormDrawerComponent,
  ],
  template: `
    <div class="refunds-admin container">
      <header class="refunds-admin__header">
        <h1>Refunds</h1>

        <mat-form-field appearance="outline" class="refunds-admin__filter" subscriptSizing="dynamic">
          <mat-label>Filter by Status</mat-label>
          <mat-select [value]="statusFilter()" (valueChange)="onStatusFilter($event)" data-testid="refund-status-filter">
            <mat-option value="">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </header>

      <section class="refunds-admin__lookup" aria-label="Look up an order to refund">
        <form [formGroup]="lookupForm" (ngSubmit)="onLookupOrder()" class="refunds-admin__lookup-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Order ID</mat-label>
            <input matInput formControlName="orderId" data-testid="order-id-input" placeholder="e.g. ord-1234" />
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            data-testid="lookup-order-btn"
            [disabled]="lookupForm.invalid || lookupLoading()"
          >
            {{ lookupLoading() ? 'Looking up...' : 'Look Up Order' }}
          </button>
        </form>

        @if (lookupError()) {
          <p class="refunds-admin__error" role="alert" data-testid="lookup-error">{{ lookupError() }}</p>
        }

        @if (lookedUpOrder()) {
          <div class="refunds-admin__order-card" data-testid="order-card">
            <div class="refunds-admin__order-row">
              <span>Order</span>
              <strong>{{ lookedUpOrder()!.orderNumber }}</strong>
            </div>
            <div class="refunds-admin__order-row">
              <span>Order Type</span>
              <span>{{ lookedUpOrder()!.guestOrder ? 'Guest' : 'Registered customer' }}</span>
            </div>
            <div class="refunds-admin__order-row">
              <span>Total</span>
              <span>{{ lookedUpOrder()!.total | currency }}</span>
            </div>
            <div class="refunds-admin__order-row">
              <span>Status</span>
              <app-status-badge [label]="lookedUpOrder()!.status" [variant]="orderVariant(lookedUpOrder()!.status)" />
            </div>

            @if (isNonRefundable(lookedUpOrder()!.status)) {
              <p class="refunds-admin__hint" data-testid="non-refundable-hint">
                Orders with status {{ lookedUpOrder()!.status }} cannot be refunded.
              </p>
            } @else {
              <form [formGroup]="refundForm" (ngSubmit)="onInitiateRefund()" novalidate>
                <mat-form-field appearance="outline">
                  <mat-label>Reason</mat-label>
                  <textarea
                    matInput
                    formControlName="reason"
                    rows="2"
                    maxlength="500"
                    data-testid="refund-reason-input"
                  ></textarea>
                </mat-form-field>
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  data-testid="initiate-refund-btn"
                  [disabled]="initiatingRefund()"
                >
                  {{ initiatingRefund() ? 'Starting refund...' : 'Initiate Refund' }}
                </button>
              </form>
            }
          </div>
        }
      </section>

      <section class="refunds-admin__saga-lookup" aria-label="Look up a refund saga by id">
        <form [formGroup]="sagaLookupForm" (ngSubmit)="onLookupSaga()" class="refunds-admin__lookup-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Refund Saga ID</mat-label>
            <input matInput formControlName="sagaId" data-testid="saga-id-input" placeholder="e.g. saga-1234" />
          </mat-form-field>
          <button
            mat-stroked-button
            type="submit"
            data-testid="lookup-saga-btn"
            [disabled]="sagaLookupForm.invalid || sagaLookupLoading()"
          >
            {{ sagaLookupLoading() ? 'Looking up...' : 'Look Up Saga' }}
          </button>
        </form>
      </section>

      <h2 class="refunds-admin__section-title">Refund Sagas</h2>
      <app-data-table
        [columns]="columns"
        [rows]="refundSagas()"
        [loading]="loading()"
        [totalElements]="totalElements()"
        [pageSize]="pageSize()"
        [pageIndex]="pageIndex()"
        emptyMessage="No refunds initiated yet."
        ariaLabel="Refund sagas table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (pageChange)="onPage($event)"
        (rowClick)="onRowClick($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="sagaVariant(row['status'])" />
        } @else if (col.key === 'refundAmount') {
          {{ row['refundAmount'] != null ? (row['refundAmount'] | currency) : '—' }}
        } @else if (col.key === 'updatedAt') {
          {{ row['updatedAt'] | date: 'medium' }}
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <app-form-drawer
        [title]="'Refund Saga ' + (selectedSaga()?.id ?? '')"
        [open]="drawerOpen()"
        (drawerClose)="closeDrawer()"
      >
        @if (selectedSaga()) {
          <div class="saga-detail">
            <div class="saga-detail__row">
              <span class="saga-detail__label">Order</span>
              <span>{{ selectedSaga()!.orderId }}</span>
            </div>
            <div class="saga-detail__row">
              <span class="saga-detail__label">Status</span>
              <app-status-badge [label]="selectedSaga()!.status" [variant]="sagaVariant(selectedSaga()!.status)" />
            </div>
            <div class="saga-detail__row">
              <span class="saga-detail__label">Current Step</span>
              <span>{{ selectedSaga()!.currentStep }}</span>
            </div>
            <div class="saga-detail__row">
              <span class="saga-detail__label">Refund Amount</span>
              <span>{{ selectedSaga()!.refundAmount != null ? (selectedSaga()!.refundAmount | currency) : '—' }}</span>
            </div>
            @if (selectedSaga()!.failureReason) {
              <div class="saga-detail__row">
                <span class="saga-detail__label">Failure Reason</span>
                <span data-testid="saga-failure-reason">{{ selectedSaga()!.failureReason }}</span>
              </div>
            }

            <button
              mat-stroked-button
              (click)="onRefreshSaga(selectedSaga()!.id)"
              data-testid="refresh-saga-btn"
              [disabled]="sagaLookupLoading()"
            >
              Refresh Status
            </button>
          </div>
        }
      </app-form-drawer>
    </div>
  `,
  styleUrl: './refunds-admin.page.scss',
})
export class RefundsAdminPage implements OnInit {
  private readonly refundService = inject(RefundAdminService);
  private readonly orderService = inject(OrderAdminService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly refundSagas = signal<RefundSagaRow[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  readonly statusFilter = signal<RefundSagaStatus | ''>('');
  readonly lookupLoading = signal(false);
  readonly lookupError = signal<string | null>(null);
  readonly lookedUpOrder = signal<Order | null>(null);
  readonly initiatingRefund = signal(false);
  readonly sagaLookupLoading = signal(false);
  readonly drawerOpen = signal(false);
  readonly selectedSaga = signal<RefundSagaState | null>(null);

  readonly statuses = REFUND_SAGA_STATUSES;

  readonly columns: TableColumn[] = [
    { key: 'orderId', label: 'Order ID' },
    { key: 'status', label: 'Status' },
    { key: 'currentStep', label: 'Current Step' },
    { key: 'refundAmount', label: 'Amount' },
    { key: 'updatedAt', label: 'Updated' },
  ];

  private currentParams: RefundFilterParams = { page: 0, size: 20 };

  lookupForm = this.fb.group({
    orderId: ['', Validators.required],
  });

  refundForm = this.fb.group({
    reason: ['', Validators.maxLength(500)],
  });

  sagaLookupForm = this.fb.group({
    sagaId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.loadRefunds();
  }

  private loadRefunds(): void {
    this.loading.set(true);
    const status = this.statusFilter();
    const params: RefundFilterParams = {
      ...this.currentParams,
      ...(status ? { status } : {}),
    };
    this.refundService.getRefunds(params).subscribe({
      next: (paged) => {
        this.refundSagas.set(paged.content as RefundSagaRow[]);
        this.totalElements.set(paged.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onStatusFilter(status: RefundSagaStatus | ''): void {
    this.statusFilter.set(status);
    this.currentParams = { ...this.currentParams, page: 0 };
    this.pageIndex.set(0);
    this.loadRefunds();
  }

  onPage(event: PageEvent): void {
    this.currentParams = { ...this.currentParams, page: event.pageIndex, size: event.pageSize };
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadRefunds();
  }

  onLookupOrder(): void {
    if (this.lookupForm.invalid) return;
    const orderId = this.lookupForm.getRawValue().orderId!;
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookedUpOrder.set(null);
    this.orderService.getOrderById(orderId).subscribe({
      next: (order) => {
        this.lookupLoading.set(false);
        this.lookedUpOrder.set(order);
      },
      error: () => {
        this.lookupLoading.set(false);
        this.lookupError.set(`Order not found: ${orderId}`);
      },
    });
  }

  onInitiateRefund(): void {
    const order = this.lookedUpOrder();
    if (!order) return;
    this.initiatingRefund.set(true);
    const reason = this.refundForm.getRawValue().reason || undefined;
    this.refundService.startRefund(order.orderId, { reason }).subscribe({
      next: () => {
        this.initiatingRefund.set(false);
        this.refundForm.reset({ reason: '' });
        this.toast.success('Refund saga started');
        this.loadRefunds();
      },
      error: () => {
        this.initiatingRefund.set(false);
      },
    });
  }

  onLookupSaga(): void {
    if (this.sagaLookupForm.invalid) return;
    const sagaId = this.sagaLookupForm.getRawValue().sagaId!;
    this.sagaLookupLoading.set(true);
    this.refundService.getRefundSaga(sagaId).subscribe({
      next: (saga) => {
        this.sagaLookupLoading.set(false);
        this.sagaLookupForm.reset({ sagaId: '' });
        this.onRowClick(saga as unknown as RefundSagaRow);
      },
      error: () => {
        this.sagaLookupLoading.set(false);
      },
    });
  }

  onRefreshSaga(sagaId: string): void {
    this.sagaLookupLoading.set(true);
    this.refundService.getRefundSaga(sagaId).subscribe({
      next: (saga) => {
        this.sagaLookupLoading.set(false);
        this.selectedSaga.set(saga);
      },
      error: () => {
        this.sagaLookupLoading.set(false);
      },
    });
  }

  onRowClick(row: RefundSagaRow): void {
    this.selectedSaga.set(row as unknown as RefundSagaState);
    this.drawerOpen.set(true);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
    this.selectedSaga.set(null);
  }

  isNonRefundable(status: OrderStatus): boolean {
    return NON_REFUNDABLE_STATUSES.includes(status);
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

  sagaVariant(status: string): BadgeVariant {
    const map: Record<RefundSagaStatus, BadgeVariant> = {
      PENDING: 'warning',
      IN_PROGRESS: 'info',
      COMPLETED: 'success',
      COMPENSATING: 'warning',
      FAILED: 'error',
      COMPENSATED: 'error',
    };
    return map[status as RefundSagaStatus] ?? 'neutral';
  }
}
