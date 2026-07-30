import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [ToastService, { provide: MatSnackBar, useValue: snackBarSpy }],
    });
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    delete (window as Window & { __ecommerceToastHost?: boolean }).__ecommerceToastHost;
  });

  describe('when the shell toast host is not mounted', () => {
    it('should fall back to MatSnackBar for success messages', () => {
      service.success('Saved');
      expect(snackBarSpy.open).toHaveBeenCalledWith('Saved', 'Close', { duration: 3000 });
    });

    it('should fall back to MatSnackBar for error messages', () => {
      service.error('Failed');
      expect(snackBarSpy.open).toHaveBeenCalledWith('Failed', 'Close', { duration: 3000 });
    });

    it('should fall back to MatSnackBar for warning messages', () => {
      service.warning('Careful');
      expect(snackBarSpy.open).toHaveBeenCalledWith('Careful', 'Close', { duration: 3000 });
    });

    it('should fall back to MatSnackBar for info messages', () => {
      service.info('FYI');
      expect(snackBarSpy.open).toHaveBeenCalledWith('FYI', 'Close', { duration: 3000 });
    });

    it('should respect a custom duration when falling back to MatSnackBar', () => {
      service.success('Saved', 5000);
      expect(snackBarSpy.open).toHaveBeenCalledWith('Saved', 'Close', { duration: 5000 });
    });
  });

  describe('when the shell toast host is mounted', () => {
    beforeEach(() => {
      (window as Window & { __ecommerceToastHost?: boolean }).__ecommerceToastHost = true;
    });

    it('should dispatch an ecommerce:toast CustomEvent instead of using MatSnackBar', () => {
      const handler = jasmine.createSpy('handler');
      window.addEventListener('ecommerce:toast', handler);

      service.success('Saved');

      expect(handler).toHaveBeenCalledTimes(1);
      const event = handler.calls.mostRecent().args[0] as CustomEvent;
      expect(event.detail).toEqual({ type: 'success', message: 'Saved', duration: undefined });
      expect(snackBarSpy.open).not.toHaveBeenCalled();

      window.removeEventListener('ecommerce:toast', handler);
    });

    it('should dispatch the correct type for error/warning/info', () => {
      const handler = jasmine.createSpy('handler');
      window.addEventListener('ecommerce:toast', handler);

      service.error('Boom');
      service.warning('Careful');
      service.info('FYI');

      const types = handler.calls.all().map((c) => (c.args[0] as CustomEvent).detail.type);
      expect(types).toEqual(['error', 'warning', 'info']);

      window.removeEventListener('ecommerce:toast', handler);
    });

    it('should include a custom duration in the dispatched event detail', () => {
      const handler = jasmine.createSpy('handler');
      window.addEventListener('ecommerce:toast', handler);

      service.info('FYI', 8000);

      const event = handler.calls.mostRecent().args[0] as CustomEvent;
      expect(event.detail).toEqual({ type: 'info', message: 'FYI', duration: 8000 });

      window.removeEventListener('ecommerce:toast', handler);
    });
  });
});
