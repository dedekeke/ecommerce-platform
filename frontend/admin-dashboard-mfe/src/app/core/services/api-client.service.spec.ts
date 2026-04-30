import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApiClientService } from './api-client.service';

describe('ApiClientService', () => {
  let service: ApiClientService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiClientService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiClientService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should make GET request to the correct URL', () => {
    service.get('/test').subscribe();
    const req = httpMock.expectOne('/api/test');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should append query params for GET requests', () => {
    service.get('/items', { page: 0, size: 10 }).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/items');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush([]);
  });

  it('should make POST request with body', () => {
    const body = { name: 'Test' };
    service.post('/items', body).subscribe();
    const req = httpMock.expectOne('/api/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: '1', ...body });
  });

  it('should make PUT request', () => {
    service.put('/items/1', { name: 'Updated' }).subscribe();
    const req = httpMock.expectOne('/api/items/1');
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('should make PATCH request', () => {
    service.patch('/users/1', { role: 'ADMIN' }).subscribe();
    const req = httpMock.expectOne('/api/users/1');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('should make DELETE request', () => {
    service.delete('/items/1').subscribe();
    const req = httpMock.expectOne('/api/items/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
