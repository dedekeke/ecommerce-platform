import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProductAdminService } from '../../core/services/product-admin.service';
import { Product, ProductStatus } from '../../core/models/product.model';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="product-detail container">
      <header class="product-detail__header">
        <button mat-icon-button routerLink="/products" aria-label="Back to products">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="product-detail__title-group">
          @if (product()) {
            <h1>{{ product()!.name }}</h1>
            <app-status-badge [label]="product()!.status" [variant]="statusVariant(product()!.status)" />
          } @else if (loading()) {
            <div class="skeleton skeleton--heading"></div>
          }
        </div>
      </header>

      @if (loading()) {
        <mat-card>
          <mat-card-content>
            <div class="skeleton skeleton--text" style="margin-bottom:16px"></div>
            <div class="skeleton skeleton--text"></div>
          </mat-card-content>
        </mat-card>
      } @else if (error()) {
        <mat-card>
          <mat-card-content>
            <p class="product-detail__error" data-testid="error-msg">Failed to load product. Please try again.</p>
            <button mat-stroked-button (click)="loadProduct()">Retry</button>
          </mat-card-content>
        </mat-card>
      } @else if (product()) {
        <mat-card>
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="onSave()" novalidate class="product-form">
              <div class="product-form__row">
                <mat-form-field appearance="outline">
                  <mat-label>Name</mat-label>
                  <input matInput formControlName="name" data-testid="field-name" />
                  @if (form.get('name')?.errors?.['required'] && form.get('name')?.touched) {
                    <mat-error>Name is required</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>SKU</mat-label>
                  <input matInput formControlName="sku" [readonly]="true" />
                  <mat-hint>Read-only</mat-hint>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="product-form__full">
                <mat-label>Description</mat-label>
                <textarea matInput formControlName="description" rows="4" data-testid="field-description"></textarea>
              </mat-form-field>

              <div class="product-form__row">
                <mat-form-field appearance="outline">
                  <mat-label>Price ($)</mat-label>
                  <input matInput type="number" formControlName="price" data-testid="field-price" />
                  @if (form.get('price')?.errors?.['min'] && form.get('price')?.touched) {
                    <mat-error>Price must be 0 or more</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Stock</mat-label>
                  <input matInput type="number" formControlName="stock" data-testid="field-stock" />
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline">
                <mat-label>Category ID</mat-label>
                <input matInput formControlName="categoryId" />
              </mat-form-field>

              <div class="product-form__actions">
                <button mat-stroked-button type="button" routerLink="/products">Cancel</button>
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  data-testid="save-btn"
                  [disabled]="form.invalid || form.pristine || saving()"
                >
                  {{ saving() ? 'Saving...' : 'Save Changes' }}
                </button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styleUrl: './product-detail.page.scss',
})
export class ProductDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductAdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly product = signal<Product | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly saving = signal(false);

  form = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    price: [0, Validators.min(0)],
    stock: [0, Validators.min(0)],
    categoryId: [''],
    sku: [''],
  });

  ngOnInit(): void {
    this.loadProduct();
  }

  loadProduct(): void {
    this.loading.set(true);
    this.error.set(false);
    const id = this.route.snapshot.paramMap.get('id')!;
    this.productService.getProductById(id).subscribe({
      next: (p) => {
        this.product.set(p);
        this.form.patchValue({
          name: p.name,
          description: p.description,
          price: p.price,
          stock: p.stock,
          categoryId: p.categoryId,
          sku: p.sku,
        });
        this.form.markAsPristine();
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  onSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const id = this.route.snapshot.paramMap.get('id')!;
    const value = this.form.getRawValue();
    this.productService.updateProduct(id, {
      name: value.name!,
      description: value.description ?? undefined,
      price: value.price!,
      stock: value.stock!,
      categoryId: value.categoryId ?? undefined,
    }).subscribe({
      next: (updated) => {
        this.product.set(updated);
        this.form.markAsPristine();
        this.saving.set(false);
        this.snackBar.open('Product updated', 'Close', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Update failed', 'Close', { duration: 3000 });
      },
    });
  }

  statusVariant(status: ProductStatus): BadgeVariant {
    const map: Record<ProductStatus, BadgeVariant> = {
      ACTIVE: 'success',
      INACTIVE: 'neutral',
      OUT_OF_STOCK: 'warning',
    };
    return map[status] ?? 'neutral';
  }
}
