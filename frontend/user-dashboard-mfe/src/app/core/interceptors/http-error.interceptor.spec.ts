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
import { httpErrorInterceptor } from './http-error.interceptor';
import { ToastService } from '../services/toast.service';

describe('httpErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let toastSpy: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'info']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastSpy },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should not toast and should pass through a successful response', () => {
    let result: unknown;
    http.get('/api/orders').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/orders');
    req.flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(toastSpy.error).not.toHaveBeenCalled();
  });

  it('should toast the server-provided message field on error', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).toHaveBeenCalledWith('Address not found');
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush({ message: 'Address not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should toast the server-provided error field when message is absent', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).toHaveBeenCalledWith('Order already cancelled');
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush({ error: 'Order already cancelled' }, { status: 409, statusText: 'Conflict' });
  });

  it('should toast a plain string error body', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).toHaveBeenCalledWith('Bad gateway upstream');
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush('Bad gateway upstream', { status: 502, statusText: 'Bad Gateway' });
  });

  it('should fall back to a generic per-status message when no server message is present', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).toHaveBeenCalledWith('Something went wrong on our end. Please try again later.');
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush(null, { status: 500, statusText: 'Server Error' });
  });

  it('should fall back to a generic default message for unmapped statuses without a server message', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).toHaveBeenCalledWith('An unexpected error occurred. Please try again.');
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush(null, { status: 418, statusText: "I'm a teapot" });
  });

  it('should skip toasting for 401 responses so the shell can handle auth', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).not.toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('should skip toasting for 403 responses so the shell can handle auth', (done) => {
    http.get('/api/orders').subscribe({
      error: () => {
        expect(toastSpy.error).not.toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush(null, { status: 403, statusText: 'Forbidden' });
  });

  it('should rethrow the error so callers can still react to it', (done) => {
    http.get('/api/orders').subscribe({
      next: () => fail('expected an error'),
      error: (err) => {
        expect(err.status).toBe(500);
        done();
      },
    });

    const req = httpMock.expectOne('/api/orders');
    req.flush(null, { status: 500, statusText: 'Server Error' });
  });
});
