import { createApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * Bootstrap the Angular admin MFE strictly inside the React-assigned container.
 *
 * Returns a destroy handle so AngularMFEWrapper can call it in its useEffect
 * cleanup, preventing Zone.js queue / subscription / HTTP leaks when the user
 * navigates away from the route that hosts this MFE.
 */
export async function bootstrap(elementId: string): Promise<() => void> {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`[admin-dashboard-mfe] Mount element #${elementId} not found in the DOM.`);
  }
  const appRef = await createApplication(appConfig);
  appRef.bootstrap(AppComponent, element);
  return () => appRef.destroy();
}
