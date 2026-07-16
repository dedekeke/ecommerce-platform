import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { InventoryAdminService } from '../../core/services/inventory-admin.service';
import { InventoryItem, InventoryStatus } from '../../core/models/inventory.model';

type InventoryRow = Record<string, unknown> & InventoryItem;

const UPDATE_TYPES = ['RESTOCK', 'ADJUSTMENT', 'DAMAGE', 'CORRECTION'];

@Component({
  selector: 'app-inventory-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    DataTableComponent,
    StatusBadgeComponent,
    FormDrawerComponent,
  ],
  template: `
    <div class="inventory-admin container">
      <header class="inventory-admin__header">
        <h1>Inventory</h1>
        <button mat-flat-button color="primary" (click)="openCreateDrawer()" data-testid="create-inventory-btn">
          <mat-icon>add</mat-icon>
          Add Inventory
        </button>
      </header>

      <section class="inventory-admin__lookup" aria-label="Look up stock by product or SKU">
        <form [formGroup]="productLookupForm" (ngSubmit)="onLookupByProductId()" class="inventory-admin__lookup-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Product ID</mat-label>
            <input matInput formControlName="productId" data-testid="product-id-input" placeholder="e.g. prod-1234" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" data-testid="lookup-by-product-btn" [disabled]="productLookupForm.invalid || lookupLoading()">
            {{ lookupLoading() ? 'Looking up...' : 'Look Up by Product ID' }}
          </button>
        </form>

        <form [formGroup]="skuLookupForm" (ngSubmit)="onLookupBySku()" class="inventory-admin__lookup-form" novalidate>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>SKU</mat-label>
            <input matInput formControlName="sku" data-testid="sku-input" placeholder="e.g. SKU-1234" />
          </mat-form-field>
          <button mat-stroked-button type="submit" data-testid="lookup-by-sku-btn" [disabled]="skuLookupForm.invalid || lookupLoading()">
            {{ lookupLoading() ? 'Looking up...' : 'Look Up by SKU' }}
          </button>
        </form>

        @if (lookupError()) {
          <p class="inventory-admin__error" role="alert" data-testid="lookup-error">{{ lookupError() }}</p>
        }

        @if (lookupResult()) {
          <div class="inventory-admin__result" data-testid="lookup-result">
            <div class="inventory-admin__row"><span>Product</span><strong>{{ lookupResult()!.productId }}</strong></div>
            <div class="inventory-admin__row"><span>SKU</span><span>{{ lookupResult()!.sku }}</span></div>
            <div class="inventory-admin__row"><span>Status</span><app-status-badge [label]="lookupResult()!.status" [variant]="statusVariant(lookupResult()!.status)" /></div>
            <div class="inventory-admin__row"><span>Quantity</span><span>{{ lookupResult()!.quantity }}</span></div>
            <div class="inventory-admin__row"><span>Reserved</span><span>{{ lookupResult()!.reservedQuantity }}</span></div>
            <div class="inventory-admin__row"><span>Available</span><span>{{ lookupResult()!.availableQuantity }}</span></div>
            <button mat-stroked-button (click)="openAdjustDrawer(lookupResult()!)" data-testid="adjust-from-lookup-btn">
              Adjust Stock
            </button>
          </div>
        }
      </section>

      <h2 class="inventory-admin__section-title">Low Stock</h2>
      <app-data-table
        [columns]="columns"
        [rows]="lowStockItems()"
        [loading]="lowStockLoading()"
        [totalElements]="lowStockItems().length"
        [pageSize]="lowStockItems().length || 10"
        [pageIndex]="0"
        emptyMessage="No low-stock items. Nice work!"
        ariaLabel="Low stock table"
        [cellTemplate]="lowStockCellTmpl"
      />

      <ng-template #lowStockCellTmpl let-row let-col="col">
        <ng-container *ngTemplateOutlet="sharedCellTmpl; context: { $implicit: row, col: col }" />
      </ng-template>

      <h2 class="inventory-admin__section-title">Needs Reorder</h2>
      <app-data-table
        [columns]="columns"
        [rows]="reorderItems()"
        [loading]="reorderLoading()"
        [totalElements]="reorderItems().length"
        [pageSize]="reorderItems().length || 10"
        [pageIndex]="0"
        emptyMessage="Nothing needs reordering right now."
        ariaLabel="Needs reorder table"
        [cellTemplate]="reorderCellTmpl"
      />

      <ng-template #reorderCellTmpl let-row let-col="col">
        <ng-container *ngTemplateOutlet="sharedCellTmpl; context: { $implicit: row, col: col }" />
      </ng-template>

      <ng-template #sharedCellTmpl let-row let-col="col">
        @if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="statusVariant(row['status'])" />
        } @else if (col.key === 'actions') {
          <button mat-stroked-button (click)="openAdjustDrawer(row)" data-testid="adjust-row-btn">Adjust Stock</button>
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <section class="inventory-admin__reservations" aria-label="Reservations">
        <h2 class="inventory-admin__section-title">Reservations</h2>
        <p data-testid="reservations-info">
          Reservation detail is not yet available via this admin API — reservations are managed internally
          (order-service calls inventory-service over gRPC) and there is no REST endpoint to list them yet.
          This is tracked as a backend follow-up.
        </p>
      </section>

      <app-form-drawer [title]="'Adjust Stock' + (adjustTarget() ? ' — ' + adjustTarget()!.productId : '')" [open]="adjustDrawerOpen()" (drawerClose)="closeAdjustDrawer()">
        <form [formGroup]="adjustForm" (ngSubmit)="onAdjust()" class="inventory-form" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Quantity Change (use negative to reduce)</mat-label>
            <input matInput type="number" formControlName="quantityChange" data-testid="adjust-quantity-input" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Update Type</mat-label>
            <mat-select formControlName="updateType" data-testid="adjust-type-input">
              @for (t of updateTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Notes</mat-label>
            <textarea matInput formControlName="notes" rows="2" data-testid="adjust-notes-input"></textarea>
          </mat-form-field>
          <div class="inventory-form__actions">
            <button mat-stroked-button type="button" (click)="closeAdjustDrawer()">Cancel</button>
            <button mat-flat-button color="primary" type="submit" data-testid="save-adjust-btn" [disabled]="adjustForm.invalid || adjusting()">
              {{ adjusting() ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </form>
      </app-form-drawer>

      <app-form-drawer title="Add Inventory" [open]="createDrawerOpen()" (drawerClose)="closeCreateDrawer()">
        <form [formGroup]="createForm" (ngSubmit)="onCreate()" class="inventory-form" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Product ID</mat-label>
            <input matInput formControlName="productId" data-testid="create-product-id-input" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>SKU</mat-label>
            <input matInput formControlName="sku" data-testid="create-sku-input" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Quantity</mat-label>
            <input matInput type="number" formControlName="quantity" data-testid="create-quantity-input" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Reorder Level</mat-label>
            <input matInput type="number" formControlName="reorderLevel" data-testid="create-reorder-level-input" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Reorder Quantity</mat-label>
            <input matInput type="number" formControlName="reorderQuantity" data-testid="create-reorder-quantity-input" />
          </mat-form-field>
          <div class="inventory-form__actions">
            <button mat-stroked-button type="button" (click)="closeCreateDrawer()">Cancel</button>
            <button mat-flat-button color="primary" type="submit" data-testid="save-create-btn" [disabled]="createForm.invalid || creating()">
              {{ creating() ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </form>
      </app-form-drawer>
    </div>
  `,
  styleUrl: './inventory-admin.page.scss',
})
export class InventoryAdminPage implements OnInit {
  private readonly inventoryService = inject(InventoryAdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly lowStockItems = signal<InventoryRow[]>([]);
  readonly reorderItems = signal<InventoryRow[]>([]);
  readonly lowStockLoading = signal(true);
  readonly reorderLoading = signal(true);

  readonly lookupLoading = signal(false);
  readonly lookupError = signal<string | null>(null);
  readonly lookupResult = signal<InventoryItem | null>(null);

  readonly createDrawerOpen = signal(false);
  readonly creating = signal(false);
  readonly adjustDrawerOpen = signal(false);
  readonly adjusting = signal(false);
  readonly adjustTarget = signal<InventoryItem | null>(null);

  readonly updateTypes = UPDATE_TYPES;

  readonly columns: TableColumn[] = [
    { key: 'productId', label: 'Product ID' },
    { key: 'sku', label: 'SKU' },
    { key: 'quantity', label: 'Qty' },
    { key: 'availableQuantity', label: 'Available' },
    { key: 'reorderLevel', label: 'Reorder Level' },
    { key: 'status', label: 'Status' },
    { key: 'actions', label: '' },
  ];

  productLookupForm = this.fb.group({
    productId: ['', Validators.required],
  });

  skuLookupForm = this.fb.group({
    sku: ['', Validators.required],
  });

  createForm = this.fb.group({
    productId: ['', Validators.required],
    sku: ['', Validators.required],
    quantity: [0, [Validators.required, Validators.min(0)]],
    reorderLevel: [0, [Validators.required, Validators.min(0)]],
    reorderQuantity: [0, [Validators.required, Validators.min(0)]],
  });

  adjustForm = this.fb.group({
    quantityChange: [0, Validators.required],
    updateType: ['RESTOCK', Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    this.inventoryService.getLowStockItems().subscribe({
      next: (items) => {
        this.lowStockItems.set(items as InventoryRow[]);
        this.lowStockLoading.set(false);
      },
      error: () => this.lowStockLoading.set(false),
    });
    this.inventoryService.getItemsNeedingReorder().subscribe({
      next: (items) => {
        this.reorderItems.set(items as InventoryRow[]);
        this.reorderLoading.set(false);
      },
      error: () => this.reorderLoading.set(false),
    });
  }

  onLookupByProductId(): void {
    if (this.productLookupForm.invalid) return;
    const productId = this.productLookupForm.getRawValue().productId!;
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookupResult.set(null);
    this.inventoryService.getByProductId(productId).subscribe({
      next: (item) => {
        this.lookupLoading.set(false);
        this.lookupResult.set(item);
      },
      error: () => {
        this.lookupLoading.set(false);
        this.lookupError.set(`No inventory found for product: ${productId}`);
      },
    });
  }

  onLookupBySku(): void {
    if (this.skuLookupForm.invalid) return;
    const sku = this.skuLookupForm.getRawValue().sku!;
    this.lookupLoading.set(true);
    this.lookupError.set(null);
    this.lookupResult.set(null);
    this.inventoryService.getBySku(sku).subscribe({
      next: (item) => {
        this.lookupLoading.set(false);
        this.lookupResult.set(item);
      },
      error: () => {
        this.lookupLoading.set(false);
        this.lookupError.set(`No inventory found for SKU: ${sku}`);
      },
    });
  }

  openCreateDrawer(): void {
    this.createForm.reset({ productId: '', sku: '', quantity: 0, reorderLevel: 0, reorderQuantity: 0 });
    this.createDrawerOpen.set(true);
  }

  closeCreateDrawer(): void {
    this.createDrawerOpen.set(false);
  }

  onCreate(): void {
    if (this.createForm.invalid) return;
    this.creating.set(true);
    const payload = this.createForm.getRawValue() as {
      productId: string; sku: string; quantity: number; reorderLevel: number; reorderQuantity: number;
    };
    this.inventoryService.createInventory(payload).subscribe({
      next: () => {
        this.creating.set(false);
        this.closeCreateDrawer();
        this.snackBar.open('Inventory created', 'Close', { duration: 3000 });
        this.refreshLists();
      },
      error: () => {
        this.creating.set(false);
        this.snackBar.open('Failed to create inventory', 'Close', { duration: 3000 });
      },
    });
  }

  openAdjustDrawer(item: InventoryItem): void {
    this.adjustTarget.set(item);
    this.adjustForm.reset({ quantityChange: 0, updateType: 'RESTOCK', notes: '' });
    this.adjustDrawerOpen.set(true);
  }

  closeAdjustDrawer(): void {
    this.adjustDrawerOpen.set(false);
    this.adjustTarget.set(null);
  }

  onAdjust(): void {
    const target = this.adjustTarget();
    if (!target || this.adjustForm.invalid) return;
    this.adjusting.set(true);
    const { quantityChange, updateType, notes } = this.adjustForm.getRawValue();
    this.inventoryService
      .updateStock(target.productId, {
        quantityChange: quantityChange!,
        updateType: updateType!,
        notes: notes || undefined,
      })
      .subscribe({
        next: (updated) => {
          this.adjusting.set(false);
          this.closeAdjustDrawer();
          if (this.lookupResult()?.productId === updated.productId) {
            this.lookupResult.set(updated);
          }
          this.snackBar.open('Stock adjusted', 'Close', { duration: 3000 });
          this.refreshLists();
        },
        error: () => {
          this.adjusting.set(false);
          this.snackBar.open('Failed to adjust stock', 'Close', { duration: 3000 });
        },
      });
  }

  statusVariant(status: InventoryStatus): BadgeVariant {
    const map: Record<InventoryStatus, BadgeVariant> = {
      IN_STOCK: 'success',
      LOW_STOCK: 'warning',
      OUT_OF_STOCK: 'error',
      DISCONTINUED: 'neutral',
    };
    return map[status] ?? 'neutral';
  }

  private refreshLists(): void {
    this.inventoryService.getLowStockItems().subscribe({ next: (items) => this.lowStockItems.set(items as InventoryRow[]) });
    this.inventoryService.getItemsNeedingReorder().subscribe({ next: (items) => this.reorderItems.set(items as InventoryRow[]) });
  }
}
