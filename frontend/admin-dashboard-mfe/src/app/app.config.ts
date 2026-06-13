import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { APP_BASE_HREF } from '@angular/common';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * APP_BASE_HREF tells Angular's PathLocationStrategy the base path the MFE is
 * mounted under in the React shell (/admin). Sub-routes rendered by Angular
 * (/admin/users, /admin/products …) stay in the browser's path history and
 * integrate with React Router's back/forward navigation — unlike hash routing
 * which would append #fragment segments that React Router never sees.
 */
const basePath =
  (globalThis as Record<string, unknown>)['__MFE_BASE_HREF'] as string | undefined
  ?? '/admin';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    { provide: APP_BASE_HREF, useValue: basePath },
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
