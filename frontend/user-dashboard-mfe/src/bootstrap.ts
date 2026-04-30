import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

export async function bootstrap(elementId: string): Promise<void> {
  const element = document.getElementById(elementId);
  if (!element) {
    throw new Error(`[user-dashboard-mfe] Mount element #${elementId} not found in the DOM.`);
  }
  await bootstrapApplication(AppComponent, appConfig);
}
