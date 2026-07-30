import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoyaltyLookupComponent } from '../../shared/components/loyalty-lookup/loyalty-lookup.component';
import { CurrencyRatesComponent } from '../../shared/components/currency-rates/currency-rates.component';
import { PromotionAdminService } from '../../core/services/promotion-admin.service';
import { ToastService } from '../../core/services/toast.service';
import { Promotion, PromotionPayload, PromotionType } from '../../core/models/promotion.model';

type PromotionRow = Record<string, unknown> & Promotion;

const PROMOTION_TYPES: PromotionType[] = ['PERCENTAGE', 'FIXED_AMOUNT', 'BUY_X_GET_Y'];

@Component({
  selector: 'app-promotions-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    DataTableComponent,
    StatusBadgeComponent,
    FormDrawerComponent,
    LoyaltyLookupComponent,
    CurrencyRatesComponent,
  ],
  template: `
    <div class="promotions-admin container">
      <header class="promotions-admin__header">
        <h1>Promotions</h1>
        <button mat-flat-button color="primary" (click)="openCreateDrawer()" data-testid="create-promotion-btn">
          <mat-icon>add</mat-icon>
          Add Promotion
        </button>
      </header>

      <section class="promotions-admin__filters" aria-label="Filter promotions">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Search</mat-label>
          <input
            matInput
            [value]="searchText()"
            (input)="onSearchInput($event)"
            data-testid="promotion-search-input"
            placeholder="Code or name"
          />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Type</mat-label>
          <mat-select [value]="typeFilter()" (valueChange)="typeFilter.set($event)" data-testid="promotion-type-filter">
            <mat-option value="">All</mat-option>
            @for (t of types; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-checkbox [checked]="activeOnly()" (change)="activeOnly.set($event.checked)" data-testid="active-only-checkbox">
          Active only
        </mat-checkbox>
      </section>

      <app-data-table
        [columns]="columns"
        [rows]="filteredPromotions()"
        [loading]="loading()"
        [totalElements]="filteredPromotions().length"
        [pageSize]="filteredPromotions().length || 10"
        [pageIndex]="0"
        emptyMessage="No promotions found. Create your first promotion."
        ariaLabel="Promotions table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (rowClick)="onEdit($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'discountValue') {
          {{ discountLabel(row) }}
        } @else if (col.key === 'usage') {
          {{ usageLabel(row) }}
        } @else if (col.key === 'status') {
          <app-status-badge [label]="statusLabel(row)" [variant]="statusVariant(row)" />
        } @else if (col.key === 'actions') {
          <div class="promotions-admin__actions" (click)="$event.stopPropagation()">
            <button mat-icon-button [attr.aria-label]="'Edit ' + row['code']" (click)="onEdit(row)" data-testid="edit-btn">
              <mat-icon>edit</mat-icon>
            </button>
            @if (row['active']) {
              <button
                mat-icon-button
                [attr.aria-label]="'Expire ' + row['code']"
                (click)="onExpire(row)"
                data-testid="expire-btn"
              >
                <mat-icon>event_busy</mat-icon>
              </button>
            }
            <button
              mat-icon-button
              color="warn"
              [attr.aria-label]="'Delete ' + row['code']"
              (click)="onDelete(row)"
              data-testid="delete-btn"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <app-form-drawer [title]="drawerTitle()" [open]="drawerOpen()" (drawerClose)="closeDrawer()">
        <form [formGroup]="promotionForm" (ngSubmit)="onSave()" class="promotion-form" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" data-testid="promotion-code-input" placeholder="SUMMER10" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" data-testid="promotion-name-input" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Description</mat-label>
            <textarea matInput formControlName="description" rows="2" data-testid="promotion-description-input"></textarea>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Type</mat-label>
            <mat-select formControlName="type" data-testid="promotion-type-input">
              @for (t of types; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Discount Value</mat-label>
            <input matInput type="number" formControlName="discountValue" data-testid="promotion-discount-input" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Minimum Purchase Amount</mat-label>
            <input matInput type="number" formControlName="minPurchaseAmount" data-testid="promotion-min-purchase-input" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Max Uses (blank = unlimited)</mat-label>
            <input matInput type="number" formControlName="maxUses" data-testid="promotion-max-uses-input" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Start Date</mat-label>
            <input matInput type="datetime-local" formControlName="startDate" data-testid="promotion-start-date-input" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>End Date</mat-label>
            <input matInput type="datetime-local" formControlName="endDate" data-testid="promotion-end-date-input" />
          </mat-form-field>

          <mat-checkbox formControlName="active" data-testid="promotion-active-input">Active</mat-checkbox>

          <div class="promotion-form__actions">
            <button mat-stroked-button type="button" (click)="closeDrawer()">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              data-testid="save-promotion-btn"
              [disabled]="promotionForm.invalid || saving()"
            >
              {{ saving() ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </form>
      </app-form-drawer>

      <app-loyalty-lookup />
      <app-currency-rates />
    </div>
  `,
  styleUrl: './promotions-admin.page.scss',
})
export class PromotionsAdminPage implements OnInit {
  private readonly promotionService = inject(PromotionAdminService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly promotions = signal<PromotionRow[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly drawerOpen = signal(false);
  readonly drawerTitle = signal('Create Promotion');
  readonly searchText = signal('');
  readonly typeFilter = signal<PromotionType | ''>('');
  readonly activeOnly = signal(false);

  readonly types = PROMOTION_TYPES;

  private editingId: number | null = null;

  readonly columns: TableColumn[] = [
    { key: 'code', label: 'Code' },
    { key: 'name', label: 'Name' },
    { key: 'type', label: 'Type' },
    { key: 'discountValue', label: 'Discount' },
    { key: 'usage', label: 'Usage' },
    { key: 'status', label: 'Status' },
    { key: 'actions', label: '' },
  ];

  readonly filteredPromotions = computed(() => {
    const search = this.searchText().trim().toLowerCase();
    const type = this.typeFilter();
    const activeOnly = this.activeOnly();
    return this.promotions().filter((p) => {
      if (search && !p.code.toLowerCase().includes(search) && !p.name.toLowerCase().includes(search)) return false;
      if (type && p.type !== type) return false;
      if (activeOnly && this.statusLabel(p) !== 'ACTIVE') return false;
      return true;
    });
  });

  promotionForm = this.fb.group({
    code: ['', [Validators.required, Validators.minLength(3), Validators.pattern(/^[A-Z0-9_-]+$/)]],
    name: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
    type: ['PERCENTAGE' as PromotionType, Validators.required],
    discountValue: [0, [Validators.required, Validators.min(0.01)]],
    minPurchaseAmount: [0, Validators.min(0)],
    maxUses: [null as number | null, Validators.min(1)],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
    active: [true],
  });

  ngOnInit(): void {
    this.loadPromotions();
  }

  loadPromotions(): void {
    this.loading.set(true);
    this.promotionService.getPromotions().subscribe({
      next: (promotions) => {
        this.promotions.set(promotions as PromotionRow[]);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onSearchInput(event: Event): void {
    this.searchText.set((event.target as HTMLInputElement).value);
  }

  openCreateDrawer(): void {
    this.editingId = null;
    this.drawerTitle.set('Create Promotion');
    this.promotionForm.reset({
      code: '', name: '', description: '', type: 'PERCENTAGE', discountValue: 0,
      minPurchaseAmount: 0, maxUses: null, startDate: '', endDate: '', active: true,
    });
    this.drawerOpen.set(true);
  }

  onEdit(row: Promotion): void {
    this.editingId = row.id;
    this.drawerTitle.set('Edit Promotion');
    this.promotionForm.patchValue({
      code: row.code,
      name: row.name,
      description: row.description ?? '',
      type: row.type,
      discountValue: row.discountValue,
      minPurchaseAmount: row.minPurchaseAmount ?? 0,
      maxUses: row.maxUses ?? null,
      startDate: toDateTimeLocal(row.startDate),
      endDate: toDateTimeLocal(row.endDate),
      active: row.active ?? true,
    });
    this.drawerOpen.set(true);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  onSave(): void {
    if (this.promotionForm.invalid) {
      this.promotionForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const v = this.promotionForm.getRawValue();
    const payload: PromotionPayload = {
      code: v.code!,
      name: v.name!,
      description: v.description || undefined,
      type: v.type!,
      discountValue: v.discountValue!,
      minPurchaseAmount: v.minPurchaseAmount ?? undefined,
      maxUses: v.maxUses ?? undefined,
      startDate: v.startDate!,
      endDate: v.endDate!,
      active: v.active ?? true,
    };

    const request$ = this.editingId
      ? this.promotionService.updatePromotion(this.editingId, payload)
      : this.promotionService.createPromotion(payload);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.closeDrawer();
        this.toast.success(this.editingId ? 'Promotion updated' : 'Promotion created');
        this.loadPromotions();
      },
      error: () => {
        this.saving.set(false);
      },
    });
  }

  onExpire(row: Promotion): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Expire Promotion',
        message: `Expire "${row.code}"? It will stop applying to new purchases immediately.`,
        confirmLabel: 'Expire',
      } as ConfirmDialogData,
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      const payload = toPayload({ ...row, active: false });
      this.promotionService.updatePromotion(row.id, payload).subscribe({
        next: () => {
          this.toast.success('Promotion expired');
          this.loadPromotions();
        },
        error: () => {},
      });
    });
  }

  onDelete(row: Promotion): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Promotion',
        message: `Delete "${row.code}"? This cannot be undone.`,
        confirmLabel: 'Delete',
      } as ConfirmDialogData,
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.promotionService.deletePromotion(row.id).subscribe({
        next: () => {
          this.toast.success('Promotion deleted');
          this.loadPromotions();
        },
        error: () => {},
      });
    });
  }

  discountLabel(row: Promotion): string {
    if (row.type === 'PERCENTAGE') return `${row.discountValue}%`;
    if (row.type === 'FIXED_AMOUNT') return `$${row.discountValue}`;
    return `${row.discountValue}`;
  }

  usageLabel(row: Promotion): string {
    return `${row.currentUses ?? 0} / ${row.maxUses ?? '∞'}`;
  }

  statusLabel(row: Promotion): string {
    if (!row.active) return 'INACTIVE';
    if (new Date(row.endDate).getTime() < Date.now()) return 'EXPIRED';
    return 'ACTIVE';
  }

  statusVariant(row: Promotion): BadgeVariant {
    const label = this.statusLabel(row);
    if (label === 'ACTIVE') return 'success';
    if (label === 'EXPIRED') return 'warning';
    return 'neutral';
  }
}

function toPayload(promotion: Promotion): PromotionPayload {
  return {
    code: promotion.code,
    name: promotion.name,
    description: promotion.description,
    type: promotion.type,
    discountValue: promotion.discountValue,
    minPurchaseAmount: promotion.minPurchaseAmount,
    maxUses: promotion.maxUses,
    startDate: promotion.startDate,
    endDate: promotion.endDate,
    active: promotion.active,
    applicableCategories: promotion.applicableCategories,
  };
}

function toDateTimeLocal(isoDate: string): string {
  // Truncate to "YYYY-MM-DDTHH:mm" for the datetime-local input.
  return isoDate?.slice(0, 16) ?? '';
}
