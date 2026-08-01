import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WishlistService } from './wishlist.service';

const STORAGE_KEY = 'user-wishlist-storage';

describe('WishlistService', () => {
  let service: WishlistService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [WishlistService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WishlistService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should start with an empty wishlist', (done) => {
    service.getItems().subscribe((items) => {
      expect(items.length).toBe(0);
      done();
    });
  });

  it('should add an item and persist it to localStorage', (done) => {
    service.addItem({
      productId: 'prod-1',
      productName: 'Test Product',
      price: 9.99,
      currency: 'USD',
      inStock: true,
    });

    service.getItems().subscribe((items) => {
      expect(items.length).toBe(1);
      expect(items[0].productName).toBe('Test Product');
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      expect(stored.length).toBe(1);
      done();
    });
  });

  it('should not add duplicate items for the same productId', () => {
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });

    let count = 0;
    service.getItems().subscribe((items) => (count = items.length));
    expect(count).toBe(1);
  });

  it('should remove an item by id', () => {
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });

    const added = (service as unknown as { items$: { getValue: () => { id: string }[] } }).items$.getValue();
    expect(added.length).toBe(1);

    // Removing an unknown id is a no-op; removing the real id empties the list.
    service.removeItem('non-existent-id');
    service.removeItem(added[0].id);

    const remaining = (service as unknown as { items$: { getValue: () => unknown[] } }).items$.getValue();
    expect(remaining.length).toBe(0);
  });

  it('should return true from isInWishlist for added product', () => {
    service.addItem({ productId: 'prod-2', productName: 'B', price: 2, currency: 'USD', inStock: false });
    expect(service.isInWishlist('prod-2')).toBeTrue();
  });

  it('should return false from isInWishlist for missing product', () => {
    expect(service.isInWishlist('prod-99')).toBeFalse();
  });

  it('should load items from localStorage on init', () => {
    const stored = [
      { id: 'w1', productId: 'p1', productName: 'Stored', price: 5, currency: 'USD', addedAt: '2024-01-01T00:00:00Z', inStock: true },
    ];
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [WishlistService, provideHttpClient(), provideHttpClientTesting()],
    });
    const freshService = TestBed.inject(WishlistService);

    let loadedItems: unknown[] = [];
    freshService.getItems().subscribe((items) => (loadedItems = items));
    expect(loadedItems.length).toBe(1);
  });

  describe('backend-enabled HTTP path (VITE_WISHLIST_BACKEND_ENABLED=true)', () => {
    let httpMock: HttpTestingController;

    beforeEach(() => {
      httpMock = TestBed.inject(HttpTestingController);
      service.setBackendEnabled(true);
      service.setCurrentUserId('42');
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('should POST to /api/wishlist/{userId}/items when adding a product and update the stream', () => {
      service.addItem({
        productId: 'prod-77',
        productName: 'HTTP Item',
        price: 10,
        currency: 'USD',
        inStock: true,
      });

      const req = httpMock.expectOne('/api/wishlist/42/items');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ productId: 'prod-77' });
      req.flush({ id: 99, productId: 'prod-77', addedAt: '2026-04-29T00:00:00Z' });

      let captured: unknown[] = [];
      service.getItems().subscribe((items) => (captured = items));
      expect(captured.length).toBe(1);
      expect((captured[0] as { productId: string }).productId).toBe('prod-77');
    });

    it('should DELETE to /api/wishlist/{userId}/items/{productId} when removing a backed item', () => {
      // seed via backend first
      service.addItem({
        productId: 'prod-rm',
        productName: 'X',
        price: 1,
        currency: 'USD',
        inStock: true,
      });
      const post = httpMock.expectOne('/api/wishlist/42/items');
      post.flush({ id: 7, productId: 'prod-rm', addedAt: '2026-04-29T00:00:00Z' });

      // grab the local id assigned by the server response
      const items = (service as unknown as {
        items$: { getValue: () => Array<{ id: string }> };
      }).items$.getValue();
      const localId = items[0].id;

      service.removeItem(localId);

      const del = httpMock.expectOne('/api/wishlist/42/items/prod-rm');
      expect(del.request.method).toBe('DELETE');
      del.flush(null);

      let after: unknown[] = [];
      service.getItems().subscribe((list) => (after = list));
      expect(after.length).toBe(0);
    });

    it('should GET /api/wishlist/{userId} when loadFromBackend is invoked', () => {
      let received: unknown = null;
      service.loadFromBackend('42').subscribe((rows) => (received = rows));

      const req = httpMock.expectOne('/api/wishlist/42');
      expect(req.request.method).toBe('GET');
      const payload = [
        { id: 1, productId: 'p-a', addedAt: '2026-04-28T10:00:00Z' },
        { id: 2, productId: 'p-b', addedAt: '2026-04-28T11:00:00Z' },
      ];
      req.flush(payload);

      expect(Array.isArray(received)).toBeTrue();
      expect((received as unknown[]).length).toBe(2);

      let pushed: unknown[] = [];
      service.getItems().subscribe((list) => (pushed = list));
      expect(pushed.length).toBe(2);
    });
  });
});
