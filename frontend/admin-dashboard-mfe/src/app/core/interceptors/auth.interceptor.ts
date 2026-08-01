import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';

declare global {
  interface Window {
    __getAuthToken?: () => string | null;
  }
}

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const token = window.__getAuthToken?.();
  if (!token) {
    return next(req);
  }
  const authReq = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  });
  return next(authReq);
};
