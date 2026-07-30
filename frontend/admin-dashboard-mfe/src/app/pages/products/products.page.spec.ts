import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductsPage } from './products.page';
import { Router, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductAdminService } from '../../core/services/product-admin.service';
import { of } from 'rxjs';
import { Product, PagedProducts } from '../../core/models/product.model';

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

describe('ProductsPage', () => {
  let fixture: ComponentFixture<ProductsPage>;
  let productServiceSpy: jasmine.SpyObj<ProductAdminService>;

  beforeEach(async () => {
    productServiceSpy = jasmine.createSpyObj('ProductAdminService', [
      'getProducts', 'createProduct', 'updateProduct', 'deleteProduct',
    ]);
    productServiceSpy.getProducts.and.returnValue(of(mockPaged));

    await TestBed.configureTestingModule({
      imports: [ProductsPage, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProductAdminService, useValue: productServiceSpy },
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

  it('should open edit drawer and pre-fill form when onEdit is called', () => {
    fixture.componentInstance.onEdit(mockProduct as unknown as Record<string, unknown> & Product);
    fixture.detectChanges();
    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.productForm.get('name')?.value).toBe('Test Product');
    expect(fixture.componentInstance.productForm.get('price')?.value).toBe(29.99);
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
});
