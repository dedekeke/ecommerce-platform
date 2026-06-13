import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { APP_BASE_HREF } from '@angular/common';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * APP_BASE_HREF is resolved via useFactory so it is read at createApplication()
 * call-time, not at module-eval time, keeping it consistent with whatever base
 * path AngularMFEWrapper has written to window.__MFE_BASE_HREF just before
 * calling bootstrap().
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    {
      provide: APP_BASE_HREF,
      useFactory: () =>
        (globalThis as Record<string, unknown>)['__MFE_BASE_HREF'] as string ?? '/admin',
    },
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
