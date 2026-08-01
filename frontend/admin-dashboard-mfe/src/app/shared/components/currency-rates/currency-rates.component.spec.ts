import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { CurrencyRatesComponent } from './currency-rates.component';
import { CurrencyAdminService } from '../../../core/services/currency-admin.service';
import { CurrencyRates } from '../../../core/models/currency.model';

const mockRates: CurrencyRates = {
  base: 'USD',
  rates: { USD: 1, EUR: 0.92, GBP: 0.79 },
};

describe('CurrencyRatesComponent', () => {
  let fixture: ComponentFixture<CurrencyRatesComponent>;
  let serviceSpy: jasmine.SpyObj<CurrencyAdminService>;

  async function setup(ratesResult = of(mockRates)) {
    TestBed.resetTestingModule();
    serviceSpy = jasmine.createSpyObj('CurrencyAdminService', ['getRates', 'convert']);
    serviceSpy.getRates.and.returnValue(ratesResult);

    await TestBed.configureTestingModule({
      imports: [CurrencyRatesComponent, NoopAnimationsModule],
      providers: [{ provide: CurrencyAdminService, useValue: serviceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(CurrencyRatesComponent);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setup();
  });

  it('should display the section heading', () => {
    expect(fixture.nativeElement.querySelector('h2').textContent.trim()).toBe('FX Rates');
  });

  it('should load and display rates on init', () => {
    expect(serviceSpy.getRates).toHaveBeenCalled();
    const rows = fixture.nativeElement.querySelectorAll('[data-testid="rate-row"]');
    expect(rows.length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('EUR');
  });

  it('should show an empty state when there are no rates', async () => {
    await setup(of({ base: 'USD', rates: {} }));
    const empty = fixture.nativeElement.querySelector('[data-testid="rates-empty"]');
    expect(empty).toBeTruthy();
  });

  it('should show an error state when loading rates fails', async () => {
    await setup(throwError(() => new Error('500')));
    const error = fixture.nativeElement.querySelector('[data-testid="rates-error"]');
    expect(error.textContent).toContain('Failed to load FX rates');
  });

  it('should convert an amount between currencies', () => {
    serviceSpy.convert.and.returnValue(of({ amount: 92, rate: 0.92 }));
    fixture.componentInstance.convertForm.setValue({ amount: 100, from: 'USD', to: 'EUR' });
    fixture.componentInstance.onConvert();
    fixture.detectChanges();

    expect(serviceSpy.convert).toHaveBeenCalledWith({ amount: 100, from: 'USD', to: 'EUR' });
    const result = fixture.nativeElement.querySelector('[data-testid="convert-result"]');
    expect(result.textContent).toContain('92');
  });

  it('should show an inline error when conversion fails', () => {
    serviceSpy.convert.and.returnValue(throwError(() => new Error('400')));
    fixture.componentInstance.convertForm.setValue({ amount: 100, from: 'USD', to: 'ZZZ' });
    fixture.componentInstance.onConvert();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="convert-error"]');
    expect(error.textContent).toContain('Conversion failed');
  });

  it('should not convert when the form is invalid', () => {
    fixture.componentInstance.convertForm.setValue({ amount: null, from: '', to: '' });
    fixture.componentInstance.onConvert();

    expect(serviceSpy.convert).not.toHaveBeenCalled();
  });
});
