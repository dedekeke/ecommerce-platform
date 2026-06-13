import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { APP_BASE_HREF } from '@angular/common';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * APP_BASE_HREF is set to the shell's mounted base path (/profile) so Angular's
 * path-based LocationStrategy resolves sub-routes relative to that prefix.
 * This keeps Angular sub-routes (/profile/orders, /profile/addresses …) in sync
 * with React Router — no hash fragments that bypass the shell's history stack.
 *
 * The value is read from window.__MFE_BASE_HREF injected by AngularMFEWrapper
 * at mount time; it falls back to '/profile' for standalone local development.
 */
const basePath =
  (globalThis as Record<string, unknown>)['__MFE_BASE_HREF'] as string | undefined
  ?? '/profile';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    { provide: APP_BASE_HREF, useValue: basePath },
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
