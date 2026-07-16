import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { InventoryAdminService } from './inventory-admin.service';
import { CreateInventoryPayload, InventoryItem, StockUpdatePayload } from '../models/inventory.model';

const mockItem: InventoryItem = {
  id: 'inv-1',
  productId: 'prod-1',
  sku: 'SKU-1',
  quantity: 100,
  reservedQuantity: 10,
  availableQuantity: 90,
  status: 'IN_STOCK',
  reorderLevel: 20,
  reorderQuantity: 50,
};

describe('InventoryAdminService', () => {
  let service: InventoryAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [InventoryAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventoryAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/inventory/product/{productId} when looking up by product id', () => {
    let result: InventoryItem | undefined;
    service.getByProductId('prod-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory/product/prod-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockItem);
    expect(result).toEqual(mockItem);
  });

  it('should GET /api/inventory/sku/{sku} when looking up by SKU', () => {
    let result: InventoryItem | undefined;
    service.getBySku('SKU-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory/sku/SKU-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockItem);
    expect(result).toEqual(mockItem);
  });

  it('should GET /api/inventory/low-stock when listing low stock items', () => {
    let result: InventoryItem[] | undefined;
    service.getLowStockItems().subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory/low-stock');
    expect(req.request.method).toBe('GET');
    req.flush([mockItem]);
    expect(result).toEqual([mockItem]);
  });

  it('should GET /api/inventory/reorder-needed when listing items needing reorder', () => {
    let result: InventoryItem[] | undefined;
    service.getItemsNeedingReorder().subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory/reorder-needed');
    expect(req.request.method).toBe('GET');
    req.flush([mockItem]);
    expect(result).toEqual([mockItem]);
  });

  it('should POST to /api/inventory with the payload when creating inventory', () => {
    const payload: CreateInventoryPayload = {
      productId: 'prod-1',
      sku: 'SKU-1',
      quantity: 100,
      reorderLevel: 20,
      reorderQuantity: 50,
    };
    let result: InventoryItem | undefined;
    service.createInventory(payload).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockItem);
    expect(result).toEqual(mockItem);
  });

  it('should PUT to /api/inventory/product/{productId}/stock with the payload when adjusting stock', () => {
    const payload: StockUpdatePayload = { quantityChange: 25, updateType: 'RESTOCK', notes: 'Weekly delivery' };
    let result: InventoryItem | undefined;
    service.updateStock('prod-1', payload).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/inventory/product/prod-1/stock');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...mockItem, quantity: 125 });
    expect(result?.quantity).toBe(125);
  });

  it('should propagate an error when the product lookup 404s', () => {
    let error: unknown;
    service.getByProductId('missing').subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/inventory/product/missing');
    req.flush({ error: 'Inventory not found' }, { status: 404, statusText: 'Not Found' });
    expect(error).toBeTruthy();
  });

  it('should propagate an error when a stock adjustment would go below reserved quantity', () => {
    let error: unknown;
    service.updateStock('prod-1', { quantityChange: -1000, updateType: 'ADJUSTMENT' }).subscribe({
      error: (e) => (error = e),
    });

    const req = httpMock.expectOne('/api/inventory/product/prod-1/stock');
    req.flush({ error: 'Cannot reduce quantity below reserved amount' }, { status: 400, statusText: 'Bad Request' });
    expect(error).toBeTruthy();
  });
});
