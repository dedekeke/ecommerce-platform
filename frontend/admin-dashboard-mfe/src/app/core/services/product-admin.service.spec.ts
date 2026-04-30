import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductAdminService } from './product-admin.service';
import { Product, PagedProducts } from '../models/product.model';

const mockProduct: Product = {
  id: 'prod-1',
  name: 'Test Product',
  description: 'A test product',
  price: 29.99,
  category: 'Electronics',
  categoryId: 'cat-1',
  imageUrls: ['https://example.com/img.jpg'],
  stock: 100,
  sku: 'SKU-001',
  status: 'ACTIVE',
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

  it('should include optional sort/search params when provided', () => {
    service.getProducts({ page: 0, size: 10, sort: 'price', direction: 'desc', search: 'test' }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/products');
    expect(req.request.params.get('sort')).toBe('price');
    expect(req.request.params.get('direction')).toBe('desc');
    expect(req.request.params.get('search')).toBe('test');
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

  it('should create a product with POST', () => {
    const payload = { name: 'New', description: 'D', price: 10, categoryId: 'c', imageUrls: [], stock: 5, sku: 'X' };
    service.createProduct(payload).subscribe();
    const req = httpMock.expectOne('/api/products');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockProduct);
  });

  it('should update a product with PUT', () => {
    const payload = { name: 'Updated Name' };
    service.updateProduct('prod-1', payload).subscribe();
    const req = httpMock.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('PUT');
    req.flush(mockProduct);
  });

  it('should delete a product with DELETE', () => {
    service.deleteProduct('prod-1').subscribe();
    const req = httpMock.expectOne('/api/products/prod-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
