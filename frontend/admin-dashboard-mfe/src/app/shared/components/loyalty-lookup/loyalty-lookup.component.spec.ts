import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { LoyaltyLookupComponent } from './loyalty-lookup.component';
import { LoyaltyAdminService } from '../../../core/services/loyalty-admin.service';
import { LoyaltyStatus } from '../../../core/models/loyalty.model';

const mockStatus: LoyaltyStatus = {
  tier: 'GOLD',
  discountPercent: 5,
  currentSpend: 1200,
  nextTier: 'PLATINUM',
  nextTierAt: 300,
};

describe('LoyaltyLookupComponent', () => {
  let fixture: ComponentFixture<LoyaltyLookupComponent>;
  let serviceSpy: jasmine.SpyObj<LoyaltyAdminService>;

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('LoyaltyAdminService', ['getLoyaltyStatus']);

    await TestBed.configureTestingModule({
      imports: [LoyaltyLookupComponent, NoopAnimationsModule],
      providers: [{ provide: LoyaltyAdminService, useValue: serviceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(LoyaltyLookupComponent);
    fixture.detectChanges();
  });

  it('should display the section heading', () => {
    expect(fixture.nativeElement.querySelector('h2').textContent.trim()).toBe('Loyalty Tiers');
  });

  it('should show a hint that tier management is read-only', () => {
    const hint = fixture.nativeElement.querySelector('[data-testid="loyalty-hint"]');
    expect(hint.textContent).toContain('read-only');
  });

  it('should look up loyalty status for a user and display the result', () => {
    serviceSpy.getLoyaltyStatus.and.returnValue(of(mockStatus));
    fixture.componentInstance.searchForm.setValue({ userId: 'user-1' });
    fixture.componentInstance.onSearch();
    fixture.detectChanges();

    expect(serviceSpy.getLoyaltyStatus).toHaveBeenCalledWith('user-1');
    const result = fixture.nativeElement.querySelector('[data-testid="loyalty-result"]');
    expect(result.textContent).toContain('GOLD');
    expect(result.textContent).toContain('PLATINUM');
  });

  it('should show an inline error when the lookup fails', () => {
    serviceSpy.getLoyaltyStatus.and.returnValue(throwError(() => new Error('404')));
    fixture.componentInstance.searchForm.setValue({ userId: 'missing' });
    fixture.componentInstance.onSearch();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="loyalty-error"]');
    expect(error.textContent).toContain('No loyalty record found for user: missing');
  });

  it('should not search when the form is invalid', () => {
    fixture.componentInstance.searchForm.setValue({ userId: '' });
    fixture.componentInstance.onSearch();

    expect(serviceSpy.getLoyaltyStatus).not.toHaveBeenCalled();
  });

  it('should disable the search button while loading', () => {
    serviceSpy.getLoyaltyStatus.and.returnValue(of(mockStatus));
    fixture.componentInstance.searchForm.setValue({ userId: 'user-1' });
    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="loyalty-search-btn"]');
    expect(btn.disabled).toBeTrue();
  });
});
