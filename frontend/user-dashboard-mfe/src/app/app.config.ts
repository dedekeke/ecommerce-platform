import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { APP_BASE_HREF } from '@angular/common';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * APP_BASE_HREF is resolved via useFactory (not useValue) so it is read at
 * createApplication() call-time rather than at module-eval time.
 *
 * This matters for userDashboard, which is dual-mounted at /profile and /orders.
 * AngularMFEWrapper writes window.__MFE_BASE_HREF immediately before calling
 * bootstrap(); a useValue binding would capture whatever value the global held
 * the first time the module was imported — potentially the wrong base path on
 * the second mount.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    {
      provide: APP_BASE_HREF,
      useFactory: () =>
        (globalThis as Record<string, unknown>)['__MFE_BASE_HREF'] as string ?? '/profile',
    },
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
