import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PromotionAdminService } from './promotion-admin.service';
import { Promotion, PromotionPayload } from '../models/promotion.model';

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
  endDate: '2026-08-31T23:59:59',
  active: true,
  createdAt: '2026-05-01T00:00:00',
  updatedAt: '2026-05-01T00:00:00',
};

const mockPayload: PromotionPayload = {
  code: 'SUMMER10',
  name: 'Summer Sale',
  description: '10% off',
  type: 'PERCENTAGE',
  discountValue: 10,
  minPurchaseAmount: 50,
  maxUses: 100,
  startDate: '2026-06-01T00:00:00',
  endDate: '2026-08-31T23:59:59',
  active: true,
};

describe('PromotionAdminService', () => {
  let service: PromotionAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PromotionAdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PromotionAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should GET /api/promotions when listing active promotions', () => {
    let result: Promotion[] | undefined;
    service.getPromotions().subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions');
    expect(req.request.method).toBe('GET');
    req.flush([mockPromotion]);
    expect(result).toEqual([mockPromotion]);
  });

  it('should GET /api/promotions/{id} when fetching a promotion by id', () => {
    let result: Promotion | undefined;
    service.getPromotionById(1).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions/1');
    expect(req.request.method).toBe('GET');
    req.flush(mockPromotion);
    expect(result).toEqual(mockPromotion);
  });

  it('should GET /api/promotions/code/{code} when fetching a promotion by code', () => {
    let result: Promotion | undefined;
    service.getPromotionByCode('SUMMER10').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions/code/SUMMER10');
    expect(req.request.method).toBe('GET');
    req.flush(mockPromotion);
    expect(result).toEqual(mockPromotion);
  });

  it('should POST to /api/promotions with the payload when creating a promotion', () => {
    let result: Promotion | undefined;
    service.createPromotion(mockPayload).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockPayload);
    req.flush(mockPromotion);
    expect(result).toEqual(mockPromotion);
  });

  it('should PUT to /api/promotions/{id} with the payload when updating a promotion', () => {
    let result: Promotion | undefined;
    service.updatePromotion(1, mockPayload).subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/promotions/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(mockPayload);
    req.flush(mockPromotion);
    expect(result).toEqual(mockPromotion);
  });

  it('should DELETE /api/promotions/{id} when deleting a promotion', () => {
    let completed = false;
    service.deletePromotion(1).subscribe(() => (completed = true));

    const req = httpMock.expectOne('/api/promotions/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    expect(completed).toBeTrue();
  });

  it('should propagate an error when creating a promotion fails validation', () => {
    let error: unknown;
    service.createPromotion(mockPayload).subscribe({ error: (e) => (error = e) });

    const req = httpMock.expectOne('/api/promotions');
    req.flush({ error: 'Code already exists' }, { status: 409, statusText: 'Conflict' });
    expect(error).toBeTruthy();
  });
});
