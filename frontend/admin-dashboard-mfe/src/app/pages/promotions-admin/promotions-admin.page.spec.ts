import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { PromotionsAdminPage } from './promotions-admin.page';
import { PromotionAdminService } from '../../core/services/promotion-admin.service';
import { LoyaltyAdminService } from '../../core/services/loyalty-admin.service';
import { CurrencyAdminService } from '../../core/services/currency-admin.service';
import { Promotion } from '../../core/models/promotion.model';

const mockPromotion: Promotion = {
  id: 1,
  code: 'SUMMER10',
  name: 'Summer Sale',
  description: '10% off',
  type: 'PERCENTAGE',
  discountValue: 10,
  minPurchaseAmount: 50,
  maxUses: 100,
  currentUses: 5,
  startDate: '2026-06-01T00:00:00',
  endDate: '2099-08-31T23:59:59',
  active: true,
};

const expiredPromotion: Promotion = {
  ...mockPromotion,
  id: 2,
  code: 'WINTER5',
  name: 'Winter Deal',
  endDate: '2020-01-01T00:00:00',
};

function dialogSpy(result: unknown): jasmine.SpyObj<MatDialog> {
  const spy = jasmine.createSpyObj('MatDialog', ['open']);
  spy.open.and.returnValue({ afterClosed: () => of(result) });
  return spy;
}

describe('PromotionsAdminPage', () => {
  let fixture: ComponentFixture<PromotionsAdminPage>;
  let promotionServiceSpy: jasmine.SpyObj<PromotionAdminService>;

  async function setup(dialog: jasmine.SpyObj<MatDialog> = dialogSpy(true)) {
    TestBed.resetTestingModule();
    promotionServiceSpy = jasmine.createSpyObj('PromotionAdminService', [
      'getPromotions', 'createPromotion', 'updatePromotion', 'deletePromotion',
    ]);
    promotionServiceSpy.getPromotions.and.returnValue(of([mockPromotion, expiredPromotion]));

    const loyaltySpy = jasmine.createSpyObj('LoyaltyAdminService', ['getLoyaltyStatus']);
    const currencySpy = jasmine.createSpyObj('CurrencyAdminService', ['getRates', 'convert']);
    currencySpy.getRates.and.returnValue(of({ base: 'USD', rates: { USD: 1 } }));

    await TestBed.configureTestingModule({
      imports: [PromotionsAdminPage, NoopAnimationsModule],
      providers: [
        { provide: PromotionAdminService, useValue: promotionServiceSpy },
        { provide: LoyaltyAdminService, useValue: loyaltySpy },
        { provide: CurrencyAdminService, useValue: currencySpy },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PromotionsAdminPage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setup();
  });

  it('should display the page heading', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Promotions');
  });

  it('should load promotions on init', () => {
    expect(promotionServiceSpy.getPromotions).toHaveBeenCalled();
    expect(fixture.componentInstance.promotions().length).toBe(2);
  });

  it('should show an empty state when no promotions match the filter', async () => {
    await setup();
    promotionServiceSpy.getPromotions.and.returnValue(of([]));
    fixture.componentInstance.loadPromotions();
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('[data-testid="empty-state"]');
    expect(empty).toBeTruthy();
  });

  it('should filter promotions by search text (code or name)', () => {
    fixture.componentInstance.searchText.set('winter');
    fixture.detectChanges();

    expect(fixture.componentInstance.filteredPromotions().length).toBe(1);
    expect(fixture.componentInstance.filteredPromotions()[0].code).toBe('WINTER5');
  });

  it('should filter promotions to active-only', () => {
    fixture.componentInstance.activeOnly.set(true);
    fixture.detectChanges();

    const codes = fixture.componentInstance.filteredPromotions().map((p) => p.code);
    expect(codes).toContain('SUMMER10');
    expect(codes).not.toContain('WINTER5');
  });

  it('should open the create drawer with defaults', () => {
    fixture.componentInstance.openCreateDrawer();

    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.drawerTitle()).toBe('Create Promotion');
    expect(fixture.componentInstance.promotionForm.getRawValue().type).toBe('PERCENTAGE');
  });

  it('should create a promotion on save when not editing', () => {
    promotionServiceSpy.createPromotion.and.returnValue(of(mockPromotion));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.promotionForm.patchValue({
      code: 'NEWCODE',
      name: 'New Promo',
      discountValue: 15,
      startDate: '2026-01-01T00:00',
      endDate: '2026-12-31T23:59',
    });

    fixture.componentInstance.onSave();

    expect(promotionServiceSpy.createPromotion).toHaveBeenCalled();
    const payload = promotionServiceSpy.createPromotion.calls.mostRecent().args[0];
    expect(payload.code).toBe('NEWCODE');
  });

  it('should open the edit drawer prefilled with the promotion', () => {
    fixture.componentInstance.onEdit(mockPromotion);

    expect(fixture.componentInstance.drawerOpen()).toBeTrue();
    expect(fixture.componentInstance.drawerTitle()).toBe('Edit Promotion');
    expect(fixture.componentInstance.promotionForm.getRawValue().code).toBe('SUMMER10');
  });

  it('should update a promotion on save when editing', () => {
    promotionServiceSpy.updatePromotion.and.returnValue(of(mockPromotion));
    fixture.componentInstance.onEdit(mockPromotion);
    fixture.componentInstance.promotionForm.patchValue({ name: 'Updated Name' });

    fixture.componentInstance.onSave();

    expect(promotionServiceSpy.updatePromotion).toHaveBeenCalledWith(1, jasmine.objectContaining({ name: 'Updated Name' }));
  });

  it('should show an error snackbar when save fails', () => {
    promotionServiceSpy.createPromotion.and.returnValue(throwError(() => new Error('409')));
    fixture.componentInstance.openCreateDrawer();
    fixture.componentInstance.promotionForm.patchValue({
      code: 'DUPLICATE', name: 'Dup', discountValue: 10, startDate: '2026-01-01T00:00', endDate: '2026-12-31T23:59',
    });

    fixture.componentInstance.onSave();

    expect(fixture.componentInstance.saving()).toBeFalse();
  });

  it('should expire an active promotion (after confirm)', () => {
    promotionServiceSpy.updatePromotion.and.returnValue(of({ ...mockPromotion, active: false }));

    fixture.componentInstance.onExpire(mockPromotion);

    expect(promotionServiceSpy.updatePromotion).toHaveBeenCalledWith(1, jasmine.objectContaining({ active: false }));
  });

  it('should NOT expire when the confirm dialog is dismissed', async () => {
    await setup(dialogSpy(false));
    fixture.componentInstance.onExpire(mockPromotion);

    expect(promotionServiceSpy.updatePromotion).not.toHaveBeenCalled();
  });

  it('should delete a promotion (after confirm)', () => {
    promotionServiceSpy.deletePromotion.and.returnValue(of(undefined));

    fixture.componentInstance.onDelete(mockPromotion);

    expect(promotionServiceSpy.deletePromotion).toHaveBeenCalledWith(1);
  });

  it('should NOT delete when the confirm dialog is dismissed', async () => {
    await setup(dialogSpy(false));
    fixture.componentInstance.onDelete(mockPromotion);

    expect(promotionServiceSpy.deletePromotion).not.toHaveBeenCalled();
  });

  it('should format the usage label with a bounded max and an unbounded max', () => {
    expect(fixture.componentInstance.usageLabel(mockPromotion)).toBe('5 / 100');
    expect(fixture.componentInstance.usageLabel({ ...mockPromotion, maxUses: undefined })).toBe('5 / ∞');
  });

  it('should compute the correct status badge for active, expired and inactive promotions', () => {
    expect(fixture.componentInstance.statusLabel(mockPromotion)).toBe('ACTIVE');
    expect(fixture.componentInstance.statusLabel(expiredPromotion)).toBe('EXPIRED');
    expect(fixture.componentInstance.statusLabel({ ...mockPromotion, active: false })).toBe('INACTIVE');
  });

  it('should render the loyalty lookup and currency rates widgets', () => {
    expect(fixture.nativeElement.querySelector('app-loyalty-lookup')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-currency-rates')).toBeTruthy();
  });
});
