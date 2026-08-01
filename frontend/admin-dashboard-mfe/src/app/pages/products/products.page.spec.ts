import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductsPage } from './products.page';
import { Router, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ProductAdminService } from '../../core/services/product-admin.service';
import { MediaService } from '../../core/services/media.service';
import { ToastService } from '../../core/services/toast.service';
import { Product, PagedProducts, productStatus } from '../../core/models/product.model';
import { MediaResponse, MediaUploadEvent } from '../../core/models/media.model';

// Shape mirrors product-service ProductResponse.
const mockProduct: Product = {
  id: 'prod-1',
  sku: 'SKU-001',
  name: 'Test Product',
  description: 'A product',
  category: { id: 3, name: 'Electronics', slug: 'electronics' },
  price: 29.99,
  currency: 'USD',
  images: [],
  stockQuantity: 50,
  active: true,
  inStock: true,
  available: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockPaged: PagedProducts = {
  content: [mockProduct],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

const mockMedia: MediaResponse = {
  id: 'media-1',
  filename: 'photo.png',
  contentType: 'image/png',
  size: 2048,
  downloadUrl: '/api/media/media-1/download',
  contentUrl: '/api/media/media-1/content',
  uploadedBy: 'user-1',
  createdAt: '2024-01-01T00:00:00Z',
};

type ProductRow = Record<string, unknown> & Product;

function asRow(product: Product): ProductRow {
  return product as unknown as ProductRow;
}

function selectFile(fixture: ComponentFixture<ProductsPage>, file: File): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="product-image-input"]');
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
  fixture.detectChanges();
}

describe('ProductsPage', () => {
  let fixture: ComponentFixture<ProductsPage>;
  let productServiceSpy: jasmine.SpyObj<ProductAdminService>;
  let mediaServiceSpy: jasmine.SpyObj<MediaService>;
  let toastSpy: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    productServiceSpy = jasmine.createSpyObj('ProductAdminService', [
      'getProducts', 'createProduct', 'updateProduct', 'deleteProduct',
    ]);
    productServiceSpy.getProducts.and.returnValue(of(mockPaged));
    mediaServiceSpy = jasmine.createSpyObj('MediaService', ['upload']);
    toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'info']);

    await TestBed.configureTestingModule({
      imports: [ProductsPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProductAdminService, useValue: productServiceSpy },
        { provide: MediaService, useValue: mediaServiceSpy },
        { provide: ToastService, useValue: toastSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductsPage);
    fixture.detectChanges();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Products');
  });

  it('should render the create product button', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="create-product-btn"]');
    expect(btn).toBeTruthy();
  });

  it('should call getProducts on init', () => {
    expect(productServiceSpy.getProducts).toHaveBeenCalledWith({ page: 0, size: 10 });
  });

  it('should request backend param names sortBy/sortDirection on sort', () => {
    fixture.componentInstance.onSort({ active: 'price', direction: 'desc' });
    expect(productServiceSpy.getProducts).toHaveBeenCalledWith(
      jasmine.objectContaining({ sortBy: 'price', sortDirection: 'desc', page: 0 })
    );
  });

  it('should open the create drawer when create button is clicked', () => {
    fixture.componentInstance.openCreateDrawer();
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.drawerTitle()).toBe('Add Product');
  });

  it('should reset the image list when opening the create drawer', () => {
    fixture.componentInstance.imageUrls.set(['leftover.jpg']);
    fixture.componentInstance.openCreateDrawer();
    expect(fixture.componentInstance.imageUrls()).toEqual([]);
  });

  it('should open edit drawer and pre-fill form with the ProductResponse fields', () => {
    fixture.componentInstance.onEdit(asRow(mockProduct));
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    const form = fixture.componentInstance.productForm;
    expect(form.get('name')?.value).toBe('Test Product');
    expect(form.get('sku')?.value).toBe('SKU-001');
    expect(form.get('price')?.value).toBe(29.99);
    expect(form.get('currency')?.value).toBe('USD');
    expect(form.get('stockQuantity')?.value).toBe(50);
    expect(form.get('categoryId')?.value).toBe(3);
  });

  it('should populate existing images as thumbnails when editing a product', () => {
    const withImages = { ...mockProduct, images: ['/api/media/a/content', '/api/media/b/content'] };
    fixture.componentInstance.onEdit(asRow(withImages));
    fixture.detectChanges();

    const thumbnails = fixture.nativeElement.querySelectorAll('[data-testid="image-thumbnail"]');
    expect(thumbnails.length).toBe(2);
    expect(fixture.componentInstance.imageUrls()).toEqual(withImages.images);
  });

  it('should close drawer when closeDrawer is called', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
  });

  it('should not save when form is invalid', () => {
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.productForm.get('name')?.setValue('');
    fixture.componentInstance.onSaveProduct();
    expect(productServiceSpy.createProduct).not.toHaveBeenCalled();
  });

  it('should not save when required sku is missing', () => {
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.productForm.patchValue({ name: 'X', price: 5, sku: '' });
    fixture.componentInstance.onSaveProduct();
    expect(productServiceSpy.createProduct).not.toHaveBeenCalled();
  });

  it('should send a ProductRequest-shaped payload on create', async () => {
    productServiceSpy.createProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.productForm.patchValue({
      name: 'New Product', sku: 'SKU-9', price: 19.99, currency: 'usd', stockQuantity: 10, categoryId: 7,
    });
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();

    expect(productServiceSpy.createProduct).toHaveBeenCalledWith(
      jasmine.objectContaining({
        name: 'New Product',
        sku: 'SKU-9',
        price: 19.99,
        currency: 'USD',
        stockQuantity: 10,
        categoryId: 7,
        images: [],
      })
    );
  });

  it('should include the current images when creating a product', async () => {
    productServiceSpy.createProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.imageUrls.set(['/api/media/x/content']);
    fixture.componentInstance.productForm.patchValue({ name: 'New Product', sku: 'SKU-9', price: 19.99 });
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();

    expect(productServiceSpy.createProduct).toHaveBeenCalledWith(
      jasmine.objectContaining({ images: ['/api/media/x/content'] })
    );
  });

  it('should include the current images and preserve active when updating a product', async () => {
    productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.onEdit(asRow({ ...mockProduct, active: false }));
    fixture.componentInstance.imageUrls.set(['/api/media/y/content']);
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();

    expect(productServiceSpy.updateProduct).toHaveBeenCalledWith(
      'prod-1',
      jasmine.objectContaining({ images: ['/api/media/y/content'], sku: 'SKU-001', currency: 'USD', active: false })
    );
  });

  it('should derive product status from active/inStock', () => {
    expect(productStatus({ active: true, inStock: true, stockQuantity: 5 })).toBe('ACTIVE');
    expect(productStatus({ active: false, inStock: true, stockQuantity: 5 })).toBe('INACTIVE');
    expect(productStatus({ active: true, inStock: false, stockQuantity: 0 })).toBe('OUT_OF_STOCK');
  });

  it('should return correct status variant', () => {
    expect(fixture.componentInstance.statusVariant('ACTIVE')).toBe('success');
    expect(fixture.componentInstance.statusVariant('INACTIVE')).toBe('neutral');
    expect(fixture.componentInstance.statusVariant('OUT_OF_STOCK')).toBe('warning');
  });

  it('should resolve the category display name from the nested category', () => {
    expect(fixture.componentInstance.categoryName(asRow(mockProduct))).toBe('Electronics');
    expect(fixture.componentInstance.categoryName(asRow({ ...mockProduct, category: null }))).toBe('—');
  });

  it('should navigate to product detail route on row click', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    fixture.componentInstance.onRowClick(asRow(mockProduct));

    expect(navigateSpy).toHaveBeenCalledWith(['/admin/products', 'prod-1']);
  });

  describe('image upload', () => {
    beforeEach(() => {
      fixture.componentInstance.openCreateDrawer();
      fixture.detectChanges();
    });

    it('should upload a valid image and add its public contentUrl on completion', () => {
      const completeEvent: MediaUploadEvent = { type: 'complete', media: mockMedia };
      mediaServiceSpy.upload.and.returnValue(of(completeEvent));
      const file = new File(['data'], 'photo.png', { type: 'image/png' });

      selectFile(fixture, file);

      expect(mediaServiceSpy.upload).toHaveBeenCalledWith(file);
      expect(fixture.componentInstance.imageUrls()).toEqual([mockMedia.contentUrl]);
      expect(fixture.componentInstance.uploading()).toBeFalse();
      expect(toastSpy.success).toHaveBeenCalledWith('Image uploaded');
    });

    it('should reflect upload progress while the file is uploading', () => {
      const progressEvent: MediaUploadEvent = { type: 'progress', progress: 42 };
      mediaServiceSpy.upload.and.returnValue(of(progressEvent));
      const file = new File(['data'], 'photo.png', { type: 'image/png' });

      selectFile(fixture, file);

      expect(fixture.componentInstance.uploading()).toBeTrue();
      expect(fixture.componentInstance.uploadProgress()).toBe(42);
      const progressBar = fixture.nativeElement.querySelector('[data-testid="upload-progress"]');
      expect(progressBar).toBeTruthy();
    });

    it('should reject a disallowed file type client-side without calling the upload service', () => {
      const file = new File(['data'], 'malware.exe', { type: 'application/octet-stream' });

      selectFile(fixture, file);

      expect(mediaServiceSpy.upload).not.toHaveBeenCalled();
      expect(fixture.componentInstance.uploadError()).toContain('Unsupported file type');
      expect(toastSpy.error).toHaveBeenCalled();
    });

    it('should reject an SVG client-side (dropped from the allowlist, ADR PR#155)', () => {
      const file = new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' });

      selectFile(fixture, file);

      expect(mediaServiceSpy.upload).not.toHaveBeenCalled();
      expect(fixture.componentInstance.uploadError()).toContain('Unsupported file type');
    });

    it('should reject a file exceeding the max size client-side without calling the upload service', () => {
      const file = new File(['data'], 'huge.png', { type: 'image/png' });
      Object.defineProperty(file, 'size', { value: 11 * 1024 * 1024 });

      selectFile(fixture, file);

      expect(mediaServiceSpy.upload).not.toHaveBeenCalled();
      expect(fixture.componentInstance.uploadError()).toContain('too large');
      expect(toastSpy.error).toHaveBeenCalled();
    });

    it('should clear the uploading state when the upload fails', () => {
      mediaServiceSpy.upload.and.returnValue(throwError(() => new Error('Upload failed')));
      const file = new File(['data'], 'photo.png', { type: 'image/png' });

      selectFile(fixture, file);

      expect(fixture.componentInstance.uploading()).toBeFalse();
      expect(fixture.componentInstance.imageUrls()).toEqual([]);
    });

    it('should remove an image from the list', () => {
      fixture.componentInstance.imageUrls.set(['/api/media/a/content', '/api/media/b/content']);
      fixture.componentInstance.removeImage('/api/media/a/content');
      expect(fixture.componentInstance.imageUrls()).toEqual(['/api/media/b/content']);
    });
  });
});
