import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductAdminService } from './product-admin.service';
import { Product, PagedProducts, ProductPayload, ProductUpdatePayload } from '../models/product.model';

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
  stockQuantity: 100,
  active: true,
  inStock: true,
  available: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockPagedProducts: PagedProducts = {
  content: [mockProduct],
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
};

describe('ProductAdminService', () => {
  let service: ProductAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProductAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch paginated products', () => {
    let result: PagedProducts | undefined;
    service.getProducts({ page: 0, size: 10 }).subscribe((r) => (result = r));
    const req = httpMock.expectOne((r) => r.url === '/api/products');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(mockPagedProducts);
    expect(result).toEqual(mockPagedProducts);
  });

  it('should map to the backend query params sortBy/sortDirection/search/categoryId', () => {
    service
      .getProducts({ page: 0, size: 10, sortBy: 'price', sortDirection: 'desc', search: 'test', categoryId: '3' })
      .subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/products');
    expect(req.request.params.get('sortBy')).toBe('price');
    expect(req.request.params.get('sortDirection')).toBe('desc');
    expect(req.request.params.get('search')).toBe('test');
    expect(req.request.params.get('categoryId')).toBe('3');
    req.flush(mockPagedProducts);
  });

  it('should fetch a single product by id', () => {
    let result: Product | undefined;
    service.getProductById('prod-1').subscribe((r) => (result = r));
    const req = httpMock.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockProduct);
    expect(result).toEqual(mockProduct);
  });

  it('should create a product with a ProductRequest-shaped POST body', () => {
    const payload: ProductPayload = {
      sku: 'SKU-9',
      name: 'New',
      description: 'D',
      categoryId: 3,
      price: 10,
      currency: 'USD',
      images: ['/api/media/media-1/content'],
      stockQuantity: 5,
    };
    service.createProduct(payload).subscribe();
    const req = httpMock.expectOne('/api/products');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockProduct);
  });

  it('should update a product with a PUT body that omits the create-only stockQuantity', () => {
    const payload: ProductUpdatePayload = {
      sku: 'SKU-001',
      name: 'Updated Name',
      price: 12,
      currency: 'USD',
      images: [],
    };
    service.updateProduct('prod-1', payload).subscribe();
    const req = httpMock.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    expect('stockQuantity' in (req.request.body as object)).toBeFalse();
    req.flush(mockProduct);
  });

  it('should change stock through the dedicated PATCH stock endpoint', () => {
    let result: Product | undefined;
    service.updateStock('prod-1', 7).subscribe((r) => (result = r));
    const req = httpMock.expectOne((r) => r.url === '/api/products/prod-1/stock');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.params.get('quantity')).toBe('7');
    req.flush({ ...mockProduct, stockQuantity: 7 });
    expect(result?.stockQuantity).toBe(7);
  });

  it('should delete a product with DELETE', () => {
    service.deleteProduct('prod-1').subscribe();
    const req = httpMock.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
