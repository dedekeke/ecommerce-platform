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
import { Product, PagedProducts } from '../../core/models/product.model';
import { MediaResponse, MediaUploadEvent } from '../../core/models/media.model';

const mockProduct: Product = {
  id: 'prod-1',
  name: 'Test Product',
  description: 'A product',
  price: 29.99,
  category: 'Electronics',
  categoryId: 'cat-1',
  imageUrls: [],
  stock: 50,
  sku: 'SKU-001',
  status: 'ACTIVE',
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
  uploadedBy: 'user-1',
  createdAt: '2024-01-01T00:00:00Z',
};

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

  it('should open the create drawer when create button is clicked', async () => {
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

  it('should open edit drawer and pre-fill form when onEdit is called', () => {
    fixture.componentInstance.onEdit(mockProduct as unknown as Record<string, unknown> & Product);
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.productForm.get('name')?.value).toBe('Test Product');
    expect(fixture.componentInstance.productForm.get('price')?.value).toBe(29.99);
  });

  it('should populate existing imageUrls as thumbnails when editing a product', () => {
    const withImages = { ...mockProduct, imageUrls: ['/api/media/a/download', '/api/media/b/download'] };
    fixture.componentInstance.onEdit(withImages as unknown as Record<string, unknown> & Product);
    fixture.detectChanges();

    const thumbnails = fixture.nativeElement.querySelectorAll('[data-testid="image-thumbnail"]');
    expect(thumbnails.length).toBe(2);
    expect(fixture.componentInstance.imageUrls()).toEqual(withImages.imageUrls);
  });

  it('should close drawer when closeDrawer is called', () => {
    fixture.componentInstance.drawerOpen.set(true);
    fixture.componentInstance.closeDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBeFalse();
  });

  it('should not save when form is invalid', () => {
    fixture.componentInstance.productForm.get('name')?.setValue('');
    fixture.componentInstance.onSaveProduct();
    expect(productServiceSpy.createProduct).not.toHaveBeenCalled();
  });

  it('should call createProduct when form is valid and no editing id', async () => {
    productServiceSpy.createProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.productForm.patchValue({ name: 'New Product', price: 19.99, stock: 10 });
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();
    expect(productServiceSpy.createProduct).toHaveBeenCalled();
  });

  it('should include the current imageUrls when creating a product', async () => {
    productServiceSpy.createProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.imageUrls.set(['/api/media/x/download']);
    fixture.componentInstance.productForm.patchValue({ name: 'New Product', price: 19.99, stock: 10 });
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();

    expect(productServiceSpy.createProduct).toHaveBeenCalledWith(
      jasmine.objectContaining({ imageUrls: ['/api/media/x/download'] })
    );
  });

  it('should include the current imageUrls when updating a product', async () => {
    productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
    fixture.componentInstance.onEdit(mockProduct as unknown as Record<string, unknown> & Product);
    fixture.componentInstance.imageUrls.set(['/api/media/y/download']);
    fixture.componentInstance.onSaveProduct();
    await fixture.whenStable();

    expect(productServiceSpy.updateProduct).toHaveBeenCalledWith(
      'prod-1',
      jasmine.objectContaining({ imageUrls: ['/api/media/y/download'] })
    );
  });

  it('should return correct status variant', () => {
    expect(fixture.componentInstance.statusVariant('ACTIVE')).toBe('success');
    expect(fixture.componentInstance.statusVariant('INACTIVE')).toBe('neutral');
    expect(fixture.componentInstance.statusVariant('OUT_OF_STOCK')).toBe('warning');
  });

  it('should navigate to product detail route on row click', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    fixture.componentInstance.onRowClick(mockProduct as unknown as Record<string, unknown> & Product);

    expect(navigateSpy).toHaveBeenCalledWith(['/admin/products', 'prod-1']);
  });

  describe('image upload', () => {
    beforeEach(() => {
      fixture.componentInstance.openCreateDrawer();
      fixture.detectChanges();
    });

    it('should upload a valid image and add it to the image list on completion', () => {
      const completeEvent: MediaUploadEvent = { type: 'complete', media: mockMedia };
      mediaServiceSpy.upload.and.returnValue(of(completeEvent));
      const file = new File(['data'], 'photo.png', { type: 'image/png' });

      selectFile(fixture, file);

      expect(mediaServiceSpy.upload).toHaveBeenCalledWith(file);
      expect(fixture.componentInstance.imageUrls()).toEqual([mockMedia.downloadUrl]);
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
      expect(fixture.componentInstance.uploadProgress()).toBe(0);
    });

    it('should remove an image from the list when the remove button is clicked', () => {
      fixture.componentInstance.imageUrls.set(['/api/media/a/download', '/api/media/b/download']);
      fixture.detectChanges();

      const removeButtons = fixture.nativeElement.querySelectorAll('[data-testid="remove-image-btn"]');
      removeButtons[0].click();
      fixture.detectChanges();

      expect(fixture.componentInstance.imageUrls()).toEqual(['/api/media/b/download']);
    });
  });
});
