import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    delete (window as Window & { __getAuthToken?: () => string | null }).__getAuthToken;
  });

  it('should pass the request through without Authorization header when no token function is set', () => {
    delete (window as Window & { __getAuthToken?: () => string | null }).__getAuthToken;

    http.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should pass the request through without Authorization header when token function returns null', () => {
    (window as Window & { __getAuthToken?: () => string | null }).__getAuthToken = () => null;

    http.get('/api/test').subscribe();

    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('should attach Bearer token when __getAuthToken returns a token', () => {
    (window as Window & { __getAuthToken?: () => string | null }).__getAuthToken = () => 'my-jwt-token';

    http.get('/api/me').subscribe();

    const req = httpMock.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
    req.flush({});
  });

  it('should not mutate the original request object', () => {
    (window as Window & { __getAuthToken?: () => string | null }).__getAuthToken = () => 'token-abc';

    http.get('/api/profile').subscribe();

    const req = httpMock.expectOne('/api/profile');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-abc');
    req.flush({});
  });
});
