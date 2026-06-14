import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Sort } from '@angular/material/sort';
import { PageEvent } from '@angular/material/paginator';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { ProductAdminService } from '../../core/services/product-admin.service';
import { Product, ProductFilterParams, ProductStatus } from '../../core/models/product.model';
import { BadgeVariant } from '../../shared/components/status-badge/status-badge.component';

type ProductRow = Record<string, unknown> & Product;

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    DataTableComponent,
    StatusBadgeComponent,
    ConfirmDialogComponent,
    FormDrawerComponent,
  ],
  template: `
    <div class="products-page container">
      <header class="products-page__header">
        <h1>Products</h1>
        <button
          mat-flat-button
          color="primary"
          (click)="openCreateDrawer()"
          data-testid="create-product-btn"
          aria-label="Create new product"
        >
          <mat-icon>add</mat-icon>
          Add Product
        </button>
      </header>

      <app-data-table
        [columns]="columns"
        [rows]="products()"
        [loading]="loading()"
        [totalElements]="totalElements()"
        [pageSize]="pageSize()"
        [pageIndex]="pageIndex()"
        emptyMessage="No products found. Create your first product."
        ariaLabel="Products table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (sortChange)="onSort($event)"
        (pageChange)="onPage($event)"
        (rowClick)="onRowClick($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="statusVariant(row['status'])" />
        } @else if (col.key === 'price') {
          {{ row['price'] | currency }}
        } @else if (col.key === 'actions') {
          <div class="products-page__actions" (click)="$event.stopPropagation()">
            <button
              mat-icon-button
              [attr.aria-label]="'Edit ' + row['name']"
              (click)="onEdit(row)"
              data-testid="edit-btn"
            >
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              color="warn"
              [attr.aria-label]="'Delete ' + row['name']"
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

      <app-form-drawer
        [title]="drawerTitle()"
        [open]="drawerOpen()"
        (drawerClose)="closeDrawer()"
      >
        <form [formGroup]="productForm" (ngSubmit)="onSaveProduct()" class="product-form" novalidate>
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" data-testid="product-name-input" />
            @if (productForm.get('name')?.errors?.['required'] && productForm.get('name')?.touched) {
              <mat-error>Name is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Price</mat-label>
            <input matInput type="number" formControlName="price" data-testid="product-price-input" />
            @if (productForm.get('price')?.errors?.['required'] && productForm.get('price')?.touched) {
              <mat-error>Price is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Stock</mat-label>
            <input matInput type="number" formControlName="stock" data-testid="product-stock-input" />
          </mat-form-field>

          <div class="product-form__actions">
            <button mat-stroked-button type="button" (click)="closeDrawer()">Cancel</button>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              data-testid="save-product-btn"
              [disabled]="productForm.invalid || saving()"
            >
              {{ saving() ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </form>
      </app-form-drawer>
    </div>
  `,
  styleUrl: './products.page.scss',
})
export class ProductsPage implements OnInit {
  private readonly productService = inject(ProductAdminService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly products = signal<ProductRow[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly pageSize = signal(10);
  readonly pageIndex = signal(0);
  readonly drawerOpen = signal(false);
  readonly drawerTitle = signal('Add Product');
  readonly saving = signal(false);

  private editingId: string | null = null;

  readonly columns: TableColumn[] = [
    { key: 'name', label: 'Name', sortable: true },
    { key: 'sku', label: 'SKU' },
    { key: 'category', label: 'Category', sortable: true },
    { key: 'price', label: 'Price', sortable: true },
    { key: 'stock', label: 'Stock', sortable: true },
    { key: 'status', label: 'Status' },
    { key: 'actions', label: '' },
  ];

  productForm = this.fb.group({
    name: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(0)]],
    stock: [0, Validators.min(0)],
  });

  private currentParams: ProductFilterParams = { page: 0, size: 10 };

  ngOnInit(): void {
    this.loadProducts();
  }

  private loadProducts(): void {
    this.loading.set(true);
    this.productService.getProducts(this.currentParams).subscribe({
      next: (paged) => {
        this.products.set(paged.content as ProductRow[]);
        this.totalElements.set(paged.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onSort(sort: Sort): void {
    this.currentParams = { ...this.currentParams, sort: sort.active, direction: sort.direction as 'asc' | 'desc', page: 0 };
    this.loadProducts();
  }

  onPage(event: PageEvent): void {
    this.currentParams = { ...this.currentParams, page: event.pageIndex, size: event.pageSize };
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadProducts();
  }

  onRowClick(row: ProductRow): void {
    this.router.navigate(['/admin/products', row['id']]);
  }

  openCreateDrawer(): void {
    this.editingId = null;
    this.drawerTitle.set('Add Product');
    this.productForm.reset({ name: '', price: 0, stock: 0 });
    this.drawerOpen.set(true);
  }

  onEdit(row: ProductRow): void {
    this.editingId = row['id'] as string;
    this.drawerTitle.set('Edit Product');
    this.productForm.patchValue({
      name: row['name'] as string,
      price: row['price'] as number,
      stock: row['stock'] as number,
    });
    this.drawerOpen.set(true);
  }

  onDelete(row: ProductRow): void {
    const data: ConfirmDialogData = {
      title: 'Delete Product',
      message: `Delete "${row['name']}"? This cannot be undone.`,
      confirmLabel: 'Delete',
    };
    const ref = this.dialog.open(ConfirmDialogComponent, { data });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.productService.deleteProduct(row['id'] as string).subscribe({
          next: () => {
            this.snackBar.open('Product deleted', 'Close', { duration: 3000 });
            this.loadProducts();
          },
          error: () => this.snackBar.open('Failed to delete product', 'Close', { duration: 3000 }),
        });
      }
    });
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  onSaveProduct(): void {
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.productForm.getRawValue();
    const payload = { name: value.name!, price: value.price!, stock: value.stock! };

    const request$ = this.editingId
      ? this.productService.updateProduct(this.editingId, payload)
      : this.productService.createProduct({ ...payload, description: '', categoryId: '', imageUrls: [], sku: '' });

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.closeDrawer();
        this.snackBar.open(this.editingId ? 'Product updated' : 'Product created', 'Close', { duration: 3000 });
        this.loadProducts();
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Save failed', 'Close', { duration: 3000 });
      },
    });
  }

  statusVariant(status: string): BadgeVariant {
    const map: Record<ProductStatus, BadgeVariant> = {
      ACTIVE: 'success',
      INACTIVE: 'neutral',
      OUT_OF_STOCK: 'warning',
    };
    return map[status as ProductStatus] ?? 'neutral';
  }
}
