import { createApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * Bootstrap the Angular MFE strictly inside the React-assigned container element.
 *
 * `createApplication()` + `ApplicationRef.bootstrap()` is used instead of
 * `bootstrapApplication()` so the Angular application is scoped to `element`
 * rather than appending to `document.body` (the default bootstrapApplication
 * behaviour), which would bleed Angular's DOM outside the shell's managed area.
 */
export async function bootstrap(elementId: string): Promise<void> {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`[user-dashboard-mfe] Mount element #${elementId} not found in the DOM.`);
  }
  const appRef = await createApplication(appConfig);
  appRef.bootstrap(AppComponent, element);
}
