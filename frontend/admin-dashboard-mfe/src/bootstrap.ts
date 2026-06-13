import { createApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * Bootstrap the Angular admin MFE strictly inside the React-assigned container.
 * See user-dashboard-mfe/src/bootstrap.ts for the full rationale.
 */
export async function bootstrap(elementId: string): Promise<void> {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`[admin-dashboard-mfe] Mount element #${elementId} not found in the DOM.`);
  }
  const appRef = await createApplication(appConfig);
  appRef.bootstrap(AppComponent, element);
}
