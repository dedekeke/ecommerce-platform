import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductDetailPage } from './product-detail.page';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ProductAdminService } from '../../core/services/product-admin.service';
import { ToastService } from '../../core/services/toast.service';
import { of, throwError } from 'rxjs';
import { Product } from '../../core/models/product.model';

// Shape mirrors product-service ProductResponse.
const mockProduct: Product = {
  id: 'prod-1',
  sku: 'SKU-001',
  name: 'Test Product',
  description: 'A test product',
  category: { id: 3, name: 'Electronics', slug: 'electronics' },
  price: 29.99,
  currency: 'USD',
  images: ['/api/media/media-1/content'],
  stockQuantity: 50,
  active: true,
  inStock: true,
  available: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('ProductDetailPage', () => {
  let fixture: ComponentFixture<ProductDetailPage>;
  let productServiceSpy: jasmine.SpyObj<ProductAdminService>;

  beforeEach(async () => {
    productServiceSpy = jasmine.createSpyObj('ProductAdminService', [
      'getProductById', 'updateProduct', 'updateStock',
    ]);
    productServiceSpy.getProductById.and.returnValue(of(mockProduct));

    await TestBed.configureTestingModule({
      imports: [ProductDetailPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: ProductAdminService, useValue: productServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'prod-1' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetailPage);
    fixture.detectChanges();
  });

  it('should display product name in the header', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Test Product');
  });

  it('should populate the form with the ProductResponse fields', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('name')?.value).toBe('Test Product');
    expect(fixture.componentInstance.form.get('price')?.value).toBe(29.99);
    expect(fixture.componentInstance.form.get('stockQuantity')?.value).toBe(50);
    expect(fixture.componentInstance.form.get('categoryId')?.value).toBe(3);
  });

  it('should show SKU as read-only', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const skuInput = fixture.nativeElement.querySelector('input[formControlName="sku"]');
    expect(skuInput.readOnly).toBeTrue();
  });

  it('should disable save button when form is pristine', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const saveBtn = fixture.nativeElement.querySelector('[data-testid="save-btn"]');
    expect(saveBtn.disabled).toBeTrue();
  });

  it('should send a full ProductRequest-shaped payload on submit', async () => {
    productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
    await fixture.whenStable();
    fixture.detectChanges();

    fixture.componentInstance.form.get('name')?.setValue('Updated Name');
    fixture.componentInstance.form.markAsDirty();
    fixture.componentInstance.onSave();
    await fixture.whenStable();

    expect(productServiceSpy.updateProduct).toHaveBeenCalledWith(
      'prod-1',
      jasmine.objectContaining({
        name: 'Updated Name',
        sku: 'SKU-001',
        currency: 'USD',
        images: ['/api/media/media-1/content'],
        active: true,
      })
    );
  });

  // stockQuantity is create-only on ProductRequest (inventory-service owns stock
  // movement), so the PUT must not carry it and stock edits use PATCH /stock.
  describe('stock ownership', () => {
    async function saveWithStock(stockQuantity: number): Promise<void> {
      await fixture.whenStable();
      fixture.detectChanges();
      fixture.componentInstance.form.patchValue({ stockQuantity });
      fixture.componentInstance.form.markAsDirty();
      fixture.componentInstance.onSave();
      await fixture.whenStable();
    }

    it('should omit stockQuantity from the update payload', async () => {
      productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
      await saveWithStock(50);

      const payload = productServiceSpy.updateProduct.calls.mostRecent().args[1];
      expect('stockQuantity' in payload).toBeFalse();
    });

    it('should not call the stock endpoint when stock is unchanged', async () => {
      productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
      await saveWithStock(50);

      expect(productServiceSpy.updateStock).not.toHaveBeenCalled();
    });

    it('should call the stock endpoint with the new quantity when stock changed', async () => {
      productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
      productServiceSpy.updateStock.and.returnValue(of({ ...mockProduct, stockQuantity: 12 }));
      await saveWithStock(12);

      expect(productServiceSpy.updateStock).toHaveBeenCalledWith('prod-1', 12);
    });

    it('should reflect the stock returned by the stock endpoint', async () => {
      productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
      productServiceSpy.updateStock.and.returnValue(of({ ...mockProduct, stockQuantity: 12 }));
      await saveWithStock(12);

      expect(fixture.componentInstance.product()?.stockQuantity).toBe(12);
    });

    it('should toast a stock-specific failure and restore server stock when the stock update fails', async () => {
      productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
      productServiceSpy.updateStock.and.returnValue(throwError(() => new Error('403')));
      const toast = TestBed.inject(ToastService);
      const errorSpy = spyOn(toast, 'error');
      await saveWithStock(12);

      expect(errorSpy).toHaveBeenCalledWith(
        'Product saved, but the stock update failed. Stock is unchanged.'
      );
      expect(fixture.componentInstance.form.get('stockQuantity')?.value).toBe(50);
    });
  });

  it('should show error message when load fails', async () => {
    productServiceSpy.getProductById.and.returnValue(throwError(() => new Error('Not found')));
    fixture = TestBed.createComponent(ProductDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const errorMsg = fixture.nativeElement.querySelector('[data-testid="error-msg"]');
    expect(errorMsg).toBeTruthy();
  });

  it('should derive the status badge from active/inStock', async () => {
    await fixture.whenStable();
    expect(fixture.componentInstance.statusOf(mockProduct)).toBe('ACTIVE');
    expect(fixture.componentInstance.statusOf({ ...mockProduct, active: false })).toBe('INACTIVE');
    expect(fixture.componentInstance.statusOf({ ...mockProduct, inStock: false })).toBe('OUT_OF_STOCK');
  });

  it('should return correct status variant', () => {
    expect(fixture.componentInstance.statusVariant('ACTIVE')).toBe('success');
    expect(fixture.componentInstance.statusVariant('OUT_OF_STOCK')).toBe('warning');
  });
});
