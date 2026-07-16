import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CurrencyAdminService } from './currency-admin.service';
import { ConvertCurrencyResult, CurrencyRates } from '../models/currency.model';

const mockRates: CurrencyRates = {
  base: 'USD',
  rates: { USD: 1, EUR: 0.92, GBP: 0.79 },
};

describe('CurrencyAdminService', () => {
  let service: CurrencyAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CurrencyAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CurrencyAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/currency/rates when listing FX rates', () => {
    let result: CurrencyRates | undefined;
    service.getRates().subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/currency/rates');
    expect(req.request.method).toBe('GET');
    req.flush(mockRates);
    expect(result).toEqual(mockRates);
  });

  it('should POST to /api/currency/convert with the conversion payload', () => {
    let result: ConvertCurrencyResult | undefined;
    service.convert({ amount: 100, from: 'USD', to: 'EUR' }).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/currency/convert');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 100, from: 'USD', to: 'EUR' });
    req.flush({ amount: 92, rate: 0.92 });
    expect(result).toEqual({ amount: 92, rate: 0.92 });
  });

  it('should propagate an error when converting an unknown currency code', () => {
    let error: unknown;
    service.convert({ amount: 100, from: 'USD', to: 'ZZZ' }).subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/currency/convert');
    req.flush({ error: 'Unknown currency code: ZZZ' }, { status: 400, statusText: 'Bad Request' });
    expect(error).toBeTruthy();
  });
});
