import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { PageEvent } from '@angular/material/paginator';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { ReturnAdminService } from '../../core/services/return-admin.service';
import { ToastService } from '../../core/services/toast.service';
import { ReturnFilterParams, ReturnRequest, ReturnStatus, ReturnSummary } from '../../core/models/return.model';

type ReturnRow = Record<string, unknown> & ReturnSummary;

const RECEIVABLE_STATUSES: ReturnStatus[] = ['AWAITING_SHIPMENT'];
const INSPECTABLE_STATUSES: ReturnStatus[] = ['RECEIVED', 'INSPECTING'];

const RETURN_STATUSES: ReturnStatus[] = [
  'REQUESTED', 'NOTIFIED', 'AWAITING_SHIPMENT', 'RECEIVED', 'INSPECTING',
  'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED', 'FAILED',
];

// Note: MatDialogModule is intentionally NOT imported here. It declares its
// own `providers: [MatDialog]`, which — as a standalone-component import —
// would shadow a TestBed-level MatDialog override with the real service.
// This component only opens ConfirmDialogComponent programmatically via
// `inject(MatDialog)`, so no mat-dialog-* template directives are needed.
@Component({
  selector: 'app-returns-admin',
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
    <div class="returns-admin container">
      <header class="returns-admin__header">
        <h1>Returns / RMA</h1>

        <mat-form-field appearance="outline" class="returns-admin__filter" subscriptSizing="dynamic">
          <mat-label>Filter by Status</mat-label>
          <mat-select [value]="statusFilter()" (valueChange)="onStatusFilter($event)" data-testid="return-status-filter">
            <mat-option value="">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </header>

      <section class="returns-admin__search" aria-label="Search returns">
        <form [formGroup]="userSearchForm" (ngSubmit)="onSearchByUser()" class="returns-admin__search-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>User ID</mat-label>
            <input matInput formControlName="userId" data-testid="user-id-input" placeholder="e.g. user-1234" />
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            data-testid="search-by-user-btn"
            [disabled]="userSearchForm.invalid || searchLoading()"
          >
            {{ searchLoading() ? 'Searching...' : 'Search by User' }}
          </button>
        </form>

        <form [formGroup]="rmaSearchForm" (ngSubmit)="onSearchByRmaId()" class="returns-admin__search-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>RMA ID</mat-label>
            <input matInput formControlName="rmaId" data-testid="rma-id-input" placeholder="e.g. rma-1234" />
          </mat-form-field>
          <button
            mat-stroked-button
            type="submit"
            data-testid="search-by-rma-btn"
            [disabled]="rmaSearchForm.invalid || searchLoading()"
          >
            {{ searchLoading() ? 'Searching...' : 'Look Up RMA' }}
          </button>
        </form>

        @if (searchError()) {
          <p class="returns-admin__error" role="alert" data-testid="search-error">{{ searchError() }}</p>
        }
      </section>

      <app-data-table
        [columns]="columns"
        [rows]="returns()"
        [loading]="loading()"
        [totalElements]="totalElements()"
        [pageSize]="pageSize()"
        [pageIndex]="pageIndex()"
        emptyMessage="No returns found. Search by user ID or RMA ID."
        ariaLabel="Returns table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (pageChange)="onPage($event)"
        (rowClick)="onRowClick($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="returnVariant(row['status'])" />
        } @else if (col.key === 'requestedAt') {
          {{ row['requestedAt'] | date: 'mediumDate' }}
        } @else if (col.key === 'outcome') {
          {{ row['outcome'] || '—' }}
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <app-form-drawer
        [title]="'Return ' + (selectedReturn()?.rmaNumber ?? '')"
        [open]="drawerOpen()"
        (drawerClose)="closeDrawer()"
      >
        @if (selectedReturn()) {
          <div class="return-detail">
            <div class="return-detail__row">
              <span class="return-detail__label">Order</span>
              <span>{{ selectedReturn()!.orderId }}</span>
            </div>
            <div class="return-detail__row">
              <span class="return-detail__label">Status</span>
              <app-status-badge [label]="selectedReturn()!.status" [variant]="returnVariant(selectedReturn()!.status)" />
            </div>
            @if (selectedReturn()!.reason) {
              <div class="return-detail__row">
                <span class="return-detail__label">Reason</span>
                <span>{{ selectedReturn()!.reason }}</span>
              </div>
            }
            @if (selectedReturn()!.returnLabelUrl) {
              <div class="return-detail__row">
                <span class="return-detail__label">Label</span>
                <a [href]="selectedReturn()!.returnLabelUrl" target="_blank" rel="noopener" data-testid="return-label-link">Shipping label</a>
              </div>
            }

            @if (selectedReturn()!.lines.length) {
              <h3 class="return-detail__section">Return Lines</h3>
              <table class="return-lines-table" data-testid="return-lines-table">
                <thead>
                  <tr>
                    <th scope="col">Item</th>
                    <th scope="col">Qty</th>
                    <th scope="col">Unit Price</th>
                    <th scope="col">Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of selectedReturn()!.lines; track line.id) {
                    <tr>
                      <td>{{ line.productId || line.orderItemId }}</td>
                      <td>{{ line.quantity }}</td>
                      <td>{{ line.unitPrice != null ? (line.unitPrice | currency) : '—' }}</td>
                      <td>{{ lineSubtotal(line) | currency }}</td>
                    </tr>
                  }
                </tbody>
              </table>
              <div class="return-detail__row">
                <span class="return-detail__label">Lines Total</span>
                <strong data-testid="lines-total">{{ linesTotal() | currency }}</strong>
              </div>
            }

            @if (canReceive(selectedReturn()!.status)) {
              <button
                mat-flat-button
                color="primary"
                (click)="onMarkReceived()"
                data-testid="mark-received-btn"
                [disabled]="processing()"
              >
                {{ processing() ? 'Processing...' : 'Mark Received' }}
              </button>
            }

            @if (canInspect(selectedReturn()!.status)) {
              <form [formGroup]="inspectForm" class="inspect-form" novalidate>
                <h3 class="return-detail__section">Inspection</h3>
                <mat-form-field appearance="outline">
                  <mat-label>Restocking Fee (%)</mat-label>
                  <input
                    matInput
                    type="number"
                    formControlName="restockingFeePercent"
                    min="0"
                    max="100"
                    data-testid="restocking-fee-input"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Condition</mat-label>
                  <input matInput formControlName="condition" data-testid="condition-input" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Notes</mat-label>
                  <textarea matInput formControlName="notes" rows="2" data-testid="notes-input"></textarea>
                </mat-form-field>

                <div class="inspect-form__actions">
                  <button
                    mat-flat-button
                    color="primary"
                    type="button"
                    (click)="onInspect('APPROVED')"
                    data-testid="approve-btn"
                    [disabled]="processing() || inspectForm.invalid"
                  >
                    Approve
                  </button>
                  <button
                    mat-stroked-button
                    color="warn"
                    type="button"
                    (click)="onInspect('REJECTED')"
                    data-testid="reject-btn"
                    [disabled]="processing()"
                  >
                    Reject
                  </button>
                </div>
              </form>
            }

            @if (selectedReturn()!.failureReason) {
              <div class="return-detail__row">
                <span class="return-detail__label">Failure Reason</span>
                <span data-testid="return-failure-reason">{{ selectedReturn()!.failureReason }}</span>
              </div>
            }
          </div>
        }
      </app-form-drawer>
    </div>
  `,
  styleUrl: './returns-admin.page.scss',
})
export class ReturnsAdminPage implements OnInit {
  private readonly returnService = inject(ReturnAdminService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly returns = signal<ReturnRow[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  readonly statusFilter = signal<ReturnStatus | ''>('');
  readonly searchLoading = signal(false);
  readonly searchError = signal<string | null>(null);
  readonly drawerOpen = signal(false);
  readonly selectedReturn = signal<ReturnRequest | null>(null);
  readonly processing = signal(false);

  readonly statuses = RETURN_STATUSES;

  readonly columns: TableColumn[] = [
    { key: 'rmaNumber', label: 'RMA #' },
    { key: 'orderId', label: 'Order ID' },
    { key: 'status', label: 'Status' },
    { key: 'requestedAt', label: 'Requested' },
    { key: 'outcome', label: 'Outcome' },
  ];

  private currentParams: ReturnFilterParams = { page: 0, size: 20 };

  userSearchForm = this.fb.group({
    userId: ['', Validators.required],
  });

  rmaSearchForm = this.fb.group({
    rmaId: ['', Validators.required],
  });

  inspectForm = this.fb.group({
    restockingFeePercent: [0, [Validators.min(0), Validators.max(100)]],
    condition: [''],
    notes: [''],
  });

  readonly linesTotal = computed(() => {
    const lines = this.selectedReturn()?.lines ?? [];
    return lines.reduce((sum, line) => sum + this.lineSubtotal(line), 0);
  });

  ngOnInit(): void {
    this.loadReturns();
  }

  private loadReturns(): void {
    this.loading.set(true);
    const status = this.statusFilter();
    const params: ReturnFilterParams = {
      ...this.currentParams,
      ...(status ? { status } : {}),
    };
    this.returnService.getReturns(params).subscribe({
      next: (paged) => {
        this.returns.set(paged.content as ReturnRow[]);
        this.totalElements.set(paged.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onStatusFilter(status: ReturnStatus | ''): void {
    this.statusFilter.set(status);
    this.currentParams = { ...this.currentParams, page: 0 };
    this.pageIndex.set(0);
    this.loadReturns();
  }

  onPage(event: PageEvent): void {
    this.currentParams = { ...this.currentParams, page: event.pageIndex, size: event.pageSize };
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadReturns();
  }

  onSearchByUser(): void {
    if (this.userSearchForm.invalid) return;
    const userId = this.userSearchForm.getRawValue().userId!;
    this.searchLoading.set(true);
    this.searchError.set(null);
    this.returnService.getReturnsByUser(userId).subscribe({
      next: (results) => {
        this.searchLoading.set(false);
        this.returns.set(results as unknown as ReturnRow[]);
        if (results.length === 0) {
          this.searchError.set(`No returns found for user: ${userId}`);
        }
      },
      error: () => {
        this.searchLoading.set(false);
        this.returns.set([]);
        this.searchError.set(`Failed to search returns for user: ${userId}`);
      },
    });
  }

  onSearchByRmaId(): void {
    if (this.rmaSearchForm.invalid) return;
    const rmaId = this.rmaSearchForm.getRawValue().rmaId!;
    this.searchLoading.set(true);
    this.searchError.set(null);
    this.returnService.getReturnById(rmaId).subscribe({
      next: (result) => {
        this.searchLoading.set(false);
        this.upsertReturn(result);
        this.openDetail(result);
      },
      error: () => {
        this.searchLoading.set(false);
        this.searchError.set(`Return not found: ${rmaId}`);
      },
    });
  }

  onRowClick(row: ReturnRow): void {
    this.returnService.getReturnById(row.id).subscribe({
      next: (full) => this.openDetail(full),
      error: () => {},
    });
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
    this.selectedReturn.set(null);
  }

  onMarkReceived(): void {
    const rma = this.selectedReturn();
    if (!rma) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Mark Received',
        message: `Confirm the parcel for RMA ${rma.rmaNumber} has been received?`,
        confirmLabel: 'Mark Received',
      } as ConfirmDialogData,
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.processing.set(true);
      this.returnService.markReceived(rma.id).subscribe({
        next: (updated) => {
          this.processing.set(false);
          this.selectedReturn.set(updated);
          this.toast.success('Return marked received');
          this.loadReturns();
        },
        error: () => {
          this.processing.set(false);
        },
      });
    });
  }

  onInspect(outcome: 'APPROVED' | 'REJECTED'): void {
    const rma = this.selectedReturn();
    if (!rma) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: outcome === 'APPROVED' ? 'Approve Return' : 'Reject Return',
        message: `Confirm you want to ${outcome === 'APPROVED' ? 'approve' : 'reject'} RMA ${rma.rmaNumber}?`,
        confirmLabel: outcome === 'APPROVED' ? 'Approve' : 'Reject',
      } as ConfirmDialogData,
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.processing.set(true);
      const { restockingFeePercent, condition, notes } = this.inspectForm.getRawValue();
      this.returnService
        .inspect(rma.id, {
          outcome,
          condition: condition || undefined,
          notes: notes || undefined,
          restockingFeePercent: restockingFeePercent ?? undefined,
        })
        .subscribe({
          next: (updated) => {
            this.processing.set(false);
            this.selectedReturn.set(updated);
            this.toast.success(`Return ${outcome.toLowerCase()}`);
            this.loadReturns();
          },
          error: () => {
            this.processing.set(false);
          },
        });
    });
  }

  canReceive(status: ReturnStatus): boolean {
    return RECEIVABLE_STATUSES.includes(status);
  }

  canInspect(status: ReturnStatus): boolean {
    return INSPECTABLE_STATUSES.includes(status);
  }

  lineSubtotal(line: { unitPrice?: number; quantity: number }): number {
    if (line.unitPrice == null) return 0;
    return line.unitPrice * line.quantity;
  }

  returnVariant(status: string): BadgeVariant {
    const map: Record<ReturnStatus, BadgeVariant> = {
      REQUESTED: 'info',
      NOTIFIED: 'info',
      AWAITING_SHIPMENT: 'warning',
      RECEIVED: 'info',
      INSPECTING: 'warning',
      APPROVED: 'success',
      REJECTED: 'error',
      COMPLETED: 'success',
      CANCELLED: 'neutral',
      FAILED: 'error',
    };
    return map[status as ReturnStatus] ?? 'neutral';
  }

  private openDetail(rma: ReturnRequest): void {
    this.selectedReturn.set(rma);
    this.inspectForm.reset({ restockingFeePercent: 0, condition: '', notes: '' });
    this.drawerOpen.set(true);
  }

  private upsertReturn(rma: ReturnRequest): void {
    const rows = this.returns();
    const idx = rows.findIndex((r) => r.id === rma.id);
    if (idx >= 0) {
      const next = [...rows];
      next[idx] = rma as unknown as ReturnRow;
      this.returns.set(next);
    } else {
      this.returns.set([rma as unknown as ReturnRow, ...rows]);
    }
  }
}
