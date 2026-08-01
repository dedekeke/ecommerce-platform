import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { InventoryAdminPage } from './inventory-admin.page';
import { InventoryAdminService } from '../../core/services/inventory-admin.service';
import { InventoryItem } from '../../core/models/inventory.model';

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

const lowStockItem: InventoryItem = { ...mockItem, id: 'inv-2', productId: 'prod-2', sku: 'SKU-2', quantity: 15, status: 'LOW_STOCK' };
const reorderItem: InventoryItem = { ...mockItem, id: 'inv-3', productId: 'prod-3', sku: 'SKU-3', quantity: 5, status: 'OUT_OF_STOCK' };

describe('InventoryAdminPage', () => {
  let fixture: ComponentFixture<InventoryAdminPage>;
  let serviceSpy: jasmine.SpyObj<InventoryAdminService>;

  async function setup() {
    TestBed.resetTestingModule();
    serviceSpy = jasmine.createSpyObj('InventoryAdminService', [
      'getByProductId', 'getBySku', 'getLowStockItems', 'getItemsNeedingReorder', 'createInventory', 'updateStock',
    ]);
    serviceSpy.getLowStockItems.and.returnValue(of([lowStockItem]));
    serviceSpy.getItemsNeedingReorder.and.returnValue(of([reorderItem]));

    await TestBed.configureTestingModule({
      imports: [InventoryAdminPage, NoopAnimationsModule],
      providers: [{ provide: InventoryAdminService, useValue: serviceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryAdminPage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setup();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Inventory');
  });

  it('should load low-stock and reorder-needed lists on init', () => {
    expect(serviceSpy.getLowStockItems).toHaveBeenCalled();
    expect(serviceSpy.getItemsNeedingReorder).toHaveBeenCalled();
    expect(fixture.componentInstance.lowStockItems().length).toBe(1);
    expect(fixture.componentInstance.reorderItems().length).toBe(1);
  });

  it('should show a reservations info panel explaining the data is not yet exposed via REST', () => {
    const panel = fixture.nativeElement.querySelector('[data-testid="reservations-info"]');
    expect(panel.textContent).toContain('not yet available');
  });

  it('should look up inventory by product id and display the result', () => {
    serviceSpy.getByProductId.and.returnValue(of(mockItem));
    fixture.componentInstance.productLookupForm.setValue({ productId: 'prod-1' });
    fixture.componentInstance.onLookupByProductId();
    fixture.detectChanges();

    expect(serviceSpy.getByProductId).toHaveBeenCalledWith('prod-1');
    const result = fixture.nativeElement.querySelector('[data-testid="lookup-result"]');
    expect(result.textContent).toContain('SKU-1');
  });

  it('should show an inline error when the product lookup fails', () => {
    serviceSpy.getByProductId.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.productLookupForm.setValue({ productId: 'missing' });
    fixture.componentInstance.onLookupByProductId();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="lookup-error"]');
    expect(error.textContent).toContain('No inventory found for product: missing');
  });

  it('should look up inventory by SKU and display the result', () => {
    serviceSpy.getBySku.and.returnValue(of(mockItem));
    fixture.componentInstance.skuLookupForm.setValue({ sku: 'SKU-1' });
    fixture.componentInstance.onLookupBySku();

    expect(serviceSpy.getBySku).toHaveBeenCalledWith('SKU-1');
    expect(fixture.componentInstance.lookupResult()?.sku).toBe('SKU-1');
  });

  it('should create a new inventory record', () => {
    serviceSpy.createInventory.and.returnValue(of(mockItem));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.createForm.setValue({
      productId: 'prod-9', sku: 'SKU-9', quantity: 100, reorderLevel: 10, reorderQuantity: 30,
    });

    fixture.componentInstance.onCreate();

    expect(serviceSpy.createInventory).toHaveBeenCalledWith({
      productId: 'prod-9', sku: 'SKU-9', quantity: 100, reorderLevel: 10, reorderQuantity: 30,
    });
    expect(fixture.componentInstance.createDrawerOpen()).toBeFalse();
  });

  it('should reset the creating flag when create fails', () => {
    serviceSpy.createInventory.and.returnValue(throwError(() => new Error('409')));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.createForm.setValue({
      productId: 'prod-9', sku: 'SKU-9', quantity: 100, reorderLevel: 10, reorderQuantity: 30,
    });

    fixture.componentInstance.onCreate();

    expect(fixture.componentInstance.creating()).toBeFalse();
  });

  it('should adjust stock for a looked-up item and refresh the result', () => {
    serviceSpy.getByProductId.and.returnValue(of(mockItem));
    fixture.componentInstance.productLookupForm.setValue({ productId: 'prod-1' });
    fixture.componentInstance.onLookupByProductId();

    fixture.componentInstance.openAdjustDrawer(mockItem);
    fixture.componentInstance.adjustForm.setValue({ quantityChange: 25, updateType: 'RESTOCK', notes: 'Delivery' });
    serviceSpy.updateStock.and.returnValue(of({ ...mockItem, quantity: 125 }));

    fixture.componentInstance.onAdjust();

    expect(serviceSpy.updateStock).toHaveBeenCalledWith('prod-1', { quantityChange: 25, updateType: 'RESTOCK', notes: 'Delivery' });
    expect(fixture.componentInstance.lookupResult()?.quantity).toBe(125);
    expect(fixture.componentInstance.adjustDrawerOpen()).toBeFalse();
  });

  it('should adjust stock from a reorder-needed row', () => {
    serviceSpy.updateStock.and.returnValue(of({ ...reorderItem, quantity: 55 }));
    fixture.componentInstance.openAdjustDrawer(reorderItem);
    fixture.componentInstance.adjustForm.setValue({ quantityChange: 50, updateType: 'RESTOCK', notes: '' });

    fixture.componentInstance.onAdjust();

    expect(serviceSpy.updateStock).toHaveBeenCalledWith('prod-3', { quantityChange: 50, updateType: 'RESTOCK', notes: undefined });
  });

  it('should reset the adjusting flag when the stock adjustment fails', () => {
    serviceSpy.updateStock.and.returnValue(throwError(() => new Error('400')));
    fixture.componentInstance.openAdjustDrawer(mockItem);
    fixture.componentInstance.adjustForm.setValue({ quantityChange: -1000, updateType: 'ADJUSTMENT', notes: '' });

    fixture.componentInstance.onAdjust();

    expect(fixture.componentInstance.adjusting()).toBeFalse();
  });

  it('should map inventory statuses to the correct badge variants', () => {
    expect(fixture.componentInstance.statusVariant('IN_STOCK')).toBe('success');
    expect(fixture.componentInstance.statusVariant('LOW_STOCK')).toBe('warning');
    expect(fixture.componentInstance.statusVariant('OUT_OF_STOCK')).toBe('error');
    expect(fixture.componentInstance.statusVariant('DISCONTINUED')).toBe('neutral');
  });
});
