import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LoyaltyAdminService } from './loyalty-admin.service';
import { LoyaltyStatus } from '../models/loyalty.model';

const mockStatus: LoyaltyStatus = {
  tier: 'GOLD',
  discountPercent: 5,
  currentSpend: 1200,
  nextTier: 'PLATINUM',
  nextTierAt: 300,
};

describe('LoyaltyAdminService', () => {
  let service: LoyaltyAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoyaltyAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LoyaltyAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/promotions/loyalty/{userId} when looking up loyalty status', () => {
    let result: LoyaltyStatus | undefined;
    service.getLoyaltyStatus('user-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions/loyalty/user-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);
    expect(result).toEqual(mockStatus);
  });

  it('should propagate an error when the user has no loyalty record', () => {
    let error: unknown;
    service.getLoyaltyStatus('missing').subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/promotions/loyalty/missing');
    req.flush({ error: 'Not found' }, { status: 404, statusText: 'Not Found' });
    expect(error).toBeTruthy();
  });
});
